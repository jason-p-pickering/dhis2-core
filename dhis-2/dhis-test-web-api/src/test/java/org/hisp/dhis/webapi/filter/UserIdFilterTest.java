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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.hisp.dhis.external.conf.DhisConfigurationProvider;
import org.hisp.dhis.user.UserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * @author Jason P. Pickering <jason@dhis2.org>
 */
@ExtendWith(MockitoExtension.class)
class UserIdFilterTest {
  private static final String USER_UID = "xE7jOejl9FI";

  @Mock private DhisConfigurationProvider dhisConfigurationProvider;

  private final HttpServletResponse res = mock(HttpServletResponse.class);

  private UserIdFilter subject;

  @BeforeEach
  void setUp() {
    MDC.clear();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void testAddsUserIdToMdcWithoutHeaderByDefault() throws Exception {
    authenticateAs(USER_UID);

    init(false);
    AtomicReference<String> inChain = doFilter();

    assertEquals(USER_UID, inChain.get());
    assertNull(MDC.get(MDC_USER_ID), "user ID must be removed from MDC after the request");
    verify(res, never()).setHeader(eq("X-User-ID"), anyString());
  }

  @Test
  void testAddsUserIdHeaderWhenEnabled() throws Exception {
    authenticateAs(USER_UID);

    init(true);
    AtomicReference<String> inChain = doFilter();

    assertEquals(USER_UID, inChain.get());
    verify(res).setHeader("X-User-ID", USER_UID);
  }

  @Test
  void testIgnoresAnonymousUser() throws Exception {
    setAuthentication(
        new AnonymousAuthenticationToken(
            "key", "anonymousUser", List.of((GrantedAuthority) () -> "ROLE_ANONYMOUS")));

    init(true);
    AtomicReference<String> inChain = doFilter();

    assertNull(inChain.get());
    verify(res, never()).setHeader(eq("X-User-ID"), anyString());
  }

  @Test
  void testIgnoresUnauthenticatedRequest() throws Exception {
    init(true);
    AtomicReference<String> inChain = doFilter();

    assertNull(inChain.get());
    verify(res, never()).setHeader(eq("X-User-ID"), anyString());
  }

  private void authenticateAs(String uid) {
    UserDetails user = mock(UserDetails.class);
    when(user.getUid()).thenReturn(uid);
    setAuthentication(
        new UsernamePasswordAuthenticationToken(user, "", List.of((GrantedAuthority) () -> "ALL")));
  }

  private static void setAuthentication(Authentication authentication) {
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
  }

  private AtomicReference<String> doFilter() throws Exception {
    AtomicReference<String> inChain = new AtomicReference<>();
    FilterChain filterChain = (request, response) -> inChain.set(MDC.get(MDC_USER_ID));

    subject.doFilter(mock(HttpServletRequest.class), res, filterChain);

    return inChain;
  }

  private void init(boolean headerEnabled) {
    when(dhisConfigurationProvider.isEnabled(LOGGING_USER_ID_HEADER_ENABLED))
        .thenReturn(headerEnabled);
    subject = new UserIdFilter(dhisConfigurationProvider);
  }
}
