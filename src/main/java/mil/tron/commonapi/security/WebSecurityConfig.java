package mil.tron.commonapi.security;

import mil.tron.commonapi.service.AppClientUserPreAuthenticatedService;
import mil.tron.commonapi.service.trace.TraceRequestFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.Customizer;

import static mil.tron.commonapi.service.DashboardUserServiceImpl.DASHBOARD_ADMIN_PRIV;
import static mil.tron.commonapi.service.DashboardUserServiceImpl.DASHBOARD_USER_PRIV;

@Configuration
@ConditionalOnProperty(name = "security.enabled", havingValue="true")
@EnableWebSecurity
public class WebSecurityConfig {

	private final TraceRequestFilter traceRequestFilter;

	private final AppClientUserPreAuthenticatedService appClientUserService;
	public WebSecurityConfig(AppClientUserPreAuthenticatedService appClientUserService,
							 TraceRequestFilter traceRequestFilter) {
		this.appClientUserService = appClientUserService;
		this.traceRequestFilter = traceRequestFilter;
	}
	
	@Bean
	public AuthenticationManager authenticationManager() {
		PreAuthenticatedAuthenticationProvider preAuthenticatedAuthenticationProvider =
				new PreAuthenticatedAuthenticationProvider();
		preAuthenticatedAuthenticationProvider.setPreAuthenticatedUserDetailsService(appClientUserService);
		return new ProviderManager(preAuthenticatedAuthenticationProvider);
	}
	
	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http, AuthenticationManager authenticationManager)
			throws Exception {
		AppClientPreAuthFilter appClientPreAuthFilter = new AppClientPreAuthFilter();
		appClientPreAuthFilter.setAuthenticationManager(authenticationManager);
		http
				.addFilter(appClientPreAuthFilter)
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/").permitAll()
						.requestMatchers("/api-docs/**", "/api-docs**").permitAll()
						.requestMatchers("/actuator/httpexchanges").denyAll()
						.requestMatchers("/actuator/health/**").hasAuthority(DASHBOARD_USER_PRIV)
						.requestMatchers("/actuator/logfile").hasAuthority(DASHBOARD_ADMIN_PRIV)
						.requestMatchers("/puckboard/**").hasAuthority(DASHBOARD_ADMIN_PRIV)
						.anyRequest().authenticated())
				.cors(Customizer.withDefaults())
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.addFilterBefore(traceRequestFilter, ExceptionTranslationFilter.class)
				.headers(headers -> headers.contentSecurityPolicy(csp ->
						csp.policyDirectives("default-src 'self' 'unsafe-inline' 'unsafe-eval' *.dso.mil data:")));
		return http.build();
    }
    
}
