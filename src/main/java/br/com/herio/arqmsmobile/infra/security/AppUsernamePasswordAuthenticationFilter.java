package br.com.herio.arqmsmobile.infra.security;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import br.com.herio.arqmsmobile.infra.security.token.TokenJwtService;

public class AppUsernamePasswordAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

	final private String MSG_TOKEN_NAO_INFORMADO = "Token de autorizacao nao enviado";
	final private String MSG_AUTHORIZATION_HEADER_INCORRETO = "Header do token de autorizacao esta incorreto ou inexistente";

	final private String TOKEN_HEADER = "Authorization";
	final private String TIPO_TOKEN_HEADER = "Bearer";

	@Autowired
	private TokenJwtService tokenJwtService;

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {

		String token = extraiTokenDoHeader(request);
		// Se o token nao existir no request deixa passar direto.
		// No momento em que usuario eh efetivamente autenticado no Spring Security este
		// filtro eh novamente disparado, por isso
		// testamos se o contexto de seguranca do Spring Security jah contem a
		// Authentication, o que significa que o usuario jah
		// foi autenticado, e nesta situacao tambem deixamos o filtro passar direto.
		if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
			UserDetails userDetails;
			try {
				userDetails = tokenJwtService.tokenJwtToUserDetais(token);
			} catch (AccessDeniedException ade) {
				HttpServletResponse httpResponse = (HttpServletResponse) response;
				httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				return;
			}
			UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails,
					null, userDetails.getAuthorities());
			SecurityContextHolder.getContext().setAuthentication(authentication);
		}
		chain.doFilter(request, response);
	}

	private String extraiTokenDoHeader(ServletRequest request) {

		HttpServletRequest httpRequest = (HttpServletRequest) request;
		String authToken = httpRequest.getHeader(this.TOKEN_HEADER);
		String tokenJwt = null;
		if (authToken != null && !authToken.startsWith("Basic")) {
			Validate.notEmpty(authToken, MSG_AUTHORIZATION_HEADER_INCORRETO);
			if (authToken.toLowerCase().startsWith(this.TIPO_TOKEN_HEADER.toLowerCase())) {
				tokenJwt = StringUtils.substringAfter(authToken, " ");
			} else {
				throw new AccessDeniedException(MSG_AUTHORIZATION_HEADER_INCORRETO);
			}
			Validate.notEmpty(tokenJwt, MSG_TOKEN_NAO_INFORMADO);
		}
		return tokenJwt;
	}

}
