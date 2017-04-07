/**
 * Copyright � 2016, viadee Unternehmensberatung GmbH
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. All advertising materials mentioning features or use of this software
 *    must display the following acknowledgement:
 *    This product includes software developed by the viadee Unternehmensberatung GmbH.
 * 4. Neither the name of the viadee Unternehmensberatung GmbH nor the
 *    names of its contributors may be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY <COPYRIGHT HOLDER> ''AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.viadee.spring.batch.operational.monitoring;

import org.apache.log4j.Logger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.support.CompositeItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import de.viadee.spring.batch.infrastructure.LoggingWrapper;
import de.viadee.spring.batch.operational.chronometer.ChronoHelper;
import de.viadee.spring.batch.operational.chronometer.Chronometer;
import de.viadee.spring.batch.persistence.SPBMItemQueue;
import de.viadee.spring.batch.persistence.types.SPBMItem;

/**
 * This class uses SpringAOP to measure any ItemProcessor on Item-Level.
 * 
 */
@Aspect
@Component
public class ItemProcessAspectListener<I> {

	@Autowired
	ChronoHelper chronoHelper;

	@Autowired
	SPBMItemQueue sPBMItemQueue;

	private static final Logger LOGGER = LoggingWrapper.getLogger(ItemProcessAspectListener.class);

	@Transactional(isolation = Isolation.READ_UNCOMMITTED)
	@Around("execution(* org.springframework.batch.item.ItemProcessor.*(..)) && args(item)")
	public Object aroundProcess(final ProceedingJoinPoint jp, final I item) throws Throwable {
		LOGGER.trace("ItemProcessor Around advice has been called");
		final ItemProcessor itemProcessor = (ItemProcessor) jp.getTarget();
		chronoHelper.setActiveAction(itemProcessor, 2, Thread.currentThread());

		// Start Chrono
		final Chronometer chronometer = new Chronometer();

		chronometer.startChronometer();
		// Proceed
		LOGGER.trace("ItemProcessor Around advice has sucessfully set up its environment");
		LOGGER.trace("ItemProcesser Around advice is now proceeding its joinpoint");
		final Object o = jp.proceed();
		// Stop Chrono
		chronometer.stop();
		if (!(itemProcessor instanceof CompositeItemProcessor)) {
			final SPBMItem sPBMItem = new SPBMItem(
					chronoHelper.getActiveActionID(Thread.currentThread()), chronoHelper.getBatchChunkListener()
							.getSPBMChunkExecution(Thread.currentThread()).getChunkExecutionID(),
					(int) chronometer.getDuration(), 0, item.toString());
			sPBMItemQueue.addItem(sPBMItem);
		}
		LOGGER.trace("ItemProcessor Around advice proceeded and has stopped its Chronometer");
		return o;
	}
}