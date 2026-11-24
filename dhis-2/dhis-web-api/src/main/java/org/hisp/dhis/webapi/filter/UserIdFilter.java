/*
 * Copyright (c) 2004-2026, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors 
 * may be used to endorse or promote products derived from this software without
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
package org.hisp.dhis.webapi.filter;

import static org.hisp.dhis.external.conf.ConfigurationKey.LOGGING_USER_ID_HEADER_ENABLED;
import static org.hisp.dhis.log.MdcKeys.MDC_USER_ID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.hisp.dhis.external.conf.DhisConfigurationProvider;
import org.hisp.dhis.user.UserDetails;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter that adds the UID of the authenticated user to the Mapped Diagnostic Context (MDC). Access
 * via {@code %X{userId}} in log4j2 pattern layouts.
 *
 * <p>When {@code logging.user_id_header.enabled} is on, the same UID is also emitted as the {@code
 * X-User-ID} response header, so a reverse proxy can record it in its access log. Combined with
 * {@code X-Request-ID} (see {@link RequestIdFilter}) this ties proxy access log lines to DHIS2 log
 * lines and to a user.
 *
 * @author Jason P. Pickering <jason@dhis2.org>
 */
@Component("userIdFilter")
public class UserIdFilter extends OncePerRequestFilter {
  private static final String X_USER_ID_HEADER = "X-User-ID";

  private final boolean headerEnabled;

  public UserIdFilter(DhisConfigurationProvider dhisConfig) {
    this.headerEnabled = dhisConfig.isEnabled(LOGGING_USER_ID_HEADER_ENABLED);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.isAuthenticated()
        && authentication.getPrincipal() instanceof UserDetails user) {
      MDC.put(MDC_USER_ID, user.getUid());
      if (headerEnabled) {
        res.setHeader(X_USER_ID_HEADER, user.getUid());
      }
    }

    try {
      chain.doFilter(req, res);
    } finally {
      MDC.remove(MDC_USER_ID);
    }
  }
}
