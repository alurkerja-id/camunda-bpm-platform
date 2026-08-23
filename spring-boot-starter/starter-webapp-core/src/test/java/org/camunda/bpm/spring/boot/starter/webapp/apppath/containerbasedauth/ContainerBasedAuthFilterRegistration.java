/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.spring.boot.starter.webapp.apppath.containerbasedauth;

import org.camunda.bpm.webapp.impl.security.auth.ContainerBasedAuthenticationFilter;
import org.springframework.core.Ordered;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.util.Collections;

@Configuration
@Order(Ordered.LOWEST_PRECEDENCE - 20) // was SecurityProperties.BASIC_AUTH_ORDER - 15 (Spring Boot 4 moved the class into spring-boot-security)
public class ContainerBasedAuthFilterRegistration {

    @Bean
    public FilterRegistrationBean<ContainerBasedAuthenticationFilter> containerBasedAuthFilter() {
        FilterRegistrationBean<ContainerBasedAuthenticationFilter> filterRegistration =
            new FilterRegistrationBean<>();
        filterRegistration.setFilter(new ContainerBasedAuthenticationFilter());
        filterRegistration.setInitParameters(Collections.singletonMap("authentication-provider",
            "org.camunda.bpm.engine.rest.security.auth.impl.ContainerBasedAuthenticationProvider"));
        filterRegistration.addUrlPatterns(ChangedAppPathContainerBasedAuthIT.MY_APP_PATH + "/*");
        return filterRegistration;
    }

}