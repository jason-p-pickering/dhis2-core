package org.hisp.dhis.webapi.controller;
/*
 * Copyright (c) 2004-2016, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import org.hisp.dhis.common.DhisApiVersion;
import org.hisp.dhis.system.scheduling.Scheduler;
import org.hisp.dhis.validation.ValidationResult;
import org.hisp.dhis.validation.ValidationResultService;
import org.hisp.dhis.validation.notification.ValidationResultNotificationTask;
import org.hisp.dhis.validation.scheduling.MonitoringTask;
import org.hisp.dhis.webapi.mvc.annotation.ApiVersion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author Stian Sandvold
 */
@Controller
@RequestMapping( "/testing" )
@ApiVersion( { DhisApiVersion.ALL, DhisApiVersion.DEFAULT } )
public class Testing
{

    @Autowired
    Scheduler scheduler;

    @Autowired
    MonitoringTask monitoringTask;

    @Autowired
    ValidationResultNotificationTask validationResultNotificationTask;

    @Autowired
    ValidationResultService validationResultService;

    @RequestMapping( value = "/runValidation", method = RequestMethod.GET )
    @ResponseStatus( value = HttpStatus.NO_CONTENT )
    public void testRunScheduledValidationAnalysis()
    {
        scheduler.executeTask( monitoringTask );
    }

    @RequestMapping( value = "/sendValidationResults", method = RequestMethod.GET )
    public
    @ResponseBody
    List<ValidationResult> restRunSendValidationResults()
    {
        List<ValidationResult> unsendt = validationResultService.getAllUnReportedValidationResults();

        scheduler.executeTask( validationResultNotificationTask );

        List<ValidationResult> still_unsendt = validationResultService.getAllUnReportedValidationResults();
        unsendt.removeAll( still_unsendt );

        return unsendt;
    }

    @RequestMapping( value = "/getValidationResults", method = RequestMethod.GET )
    public
    @ResponseBody
    List<ValidationResult> getValidationResults( @RequestParam boolean includeSent )
    {
        if ( includeSent )
        {
            return validationResultService.getAllValidationResults();
        }
        else
        {
            return validationResultService.getAllUnReportedValidationResults();
        }
    }
}
