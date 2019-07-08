package br.com.herio.arqmsmobile.service;

import java.util.Base64;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import br.com.herio.arqmsmobile.dominio.Usuario;
import br.com.herio.arqmsmobile.dominio.UsuarioRepository;
import br.com.herio.arqmsmobile.infra.excecao.ExcecaoNegocio;

@Service
public class UsuarioService {

	@Autowired
	protected JavaMailSender javaMailSender;

	@Autowired
	protected UsuarioRepository usuarioRepository;

	public String recuperarSenha(String login) {
		Usuario usuario = usuarioRepository.findByLogin(login).get();
		if (usuario == null) {
			throw new ExcecaoNegocio(String.format("Usuário de login %s inexistente", login));
		}
		return sendEmailWithAttachment(usuario);
	}

	public String sendEmailWithAttachment(Usuario usuario) {

		try {
			MimeMessage msg = javaMailSender.createMimeMessage();
			// true = multipart message
			MimeMessageHelper helper = new MimeMessageHelper(msg, true);
			helper.setTo(usuario.getEmail());
			helper.setSubject("JurisApps - Recuperação de senha");

			// true = text/html
			String email = new StringBuilder("<h1>Juris Apps - Recuperação de senha</h1><br/><br/>")
					.append("Olá %s, <br/><br/> Sua senha descriptograda é: <b>%s</b><br/><br/>")
					.append("Entre no App e caso queira trocá-la, vá em: Configurações > Atualize seus dados.<br/><br/>")
					.append("<img src='https://noticias-juridicas.herokuapp.com/publico/icone_app.png'/>")
					.append("Atenciosamente, Juris Apps.").toString();
			helper.setText(String.format(email, usuario.getNome(),
					Base64.getEncoder().encodeToString(usuario.getSenha().getBytes())), true);

			helper.addAttachment("my_photo.png", new ClassPathResource("android.png"));

			javaMailSender.send(msg);
			return "E-mail enviado com sucesso! Verifique sua caixa de e-mail.";
		} catch (MessagingException e) {
			throw new RuntimeException("Erro ao enviar email", e);
		}
	}
}
