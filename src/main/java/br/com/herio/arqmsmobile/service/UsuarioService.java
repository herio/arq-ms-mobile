package br.com.herio.arqmsmobile.service;

import java.util.Base64;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import br.com.herio.arqmsmobile.dominio.Usuario;
import br.com.herio.arqmsmobile.dominio.UsuarioRepository;
import br.com.herio.arqmsmobile.dto.EnumSistema;
import br.com.herio.arqmsmobile.infra.excecao.ExcecaoNegocio;

@Service
public class UsuarioService {

	@Autowired
	protected UsuarioRepository usuarioRepository;

	@Autowired
	protected AtivacaoUsuarioService ativacaoUsuarioService;

	@Autowired
	protected AutenticacaoService autenticacaoService;

	@Autowired
	protected EnviadorEmailService enviadorEmailService;

	@Autowired
	private FileStorageService fileStorageService;

	public Usuario criarUsuario(Usuario usuario, EnumSistema sistema) {
		if (usuario.getId() != null) {
			throw new IllegalArgumentException("Informe um novo usu�rio (sem id)!");
		}
		// cria
		usuario.valida();
		usuario.setSenha(Base64.getEncoder().encodeToString(usuario.getSenha().getBytes()));
		usuario = usuarioRepository.save(usuario);
		usuario.setToken(autenticacaoService.criaTokenJwt(usuario));
		ativacaoUsuarioService.gerarAtivacaoUsuario(usuario.getId());

		// enviaEmail
		enviadorEmailService.enviaEmailBoasVindas(usuario, sistema);
		return usuario;
	}

	public Usuario atualizarUsuario(Long idUsuario, EnumSistema sistema, Usuario usuario) {
		if (idUsuario == null) {
			throw new IllegalArgumentException("Informe um usu�rio j� existente (com id)!");
		}
		// atualiza
		Usuario usuarioBd = usuarioRepository.findById(idUsuario).get();
		usuarioBd.setLogin(usuario.getLogin());
		usuarioBd.setNome(usuario.getNome());
		usuarioBd.setSenha(Base64.getEncoder().encodeToString(usuario.getSenha().getBytes()));
		usuarioBd.setEmail(usuario.getEmail());
		usuarioBd.valida();
		usuarioBd = usuarioRepository.save(usuarioBd);

		// enviaEmail
		enviadorEmailService.enviaEmailAtualizacaoDados(usuarioBd, sistema);
		return usuarioBd;
	}

	public Usuario uploadFoto(Long idUsuario, EnumSistema sistema, MultipartFile file) {
		Usuario usuario = usuarioRepository.findById(idUsuario).get();
		String fileUri = fileStorageService.storeFile(file);
		usuario.setUrlFoto(fileUri);
		usuarioRepository.save(usuario);

		// enviaEmail
		enviadorEmailService.enviaEmailAtualizacaoDados(usuario, sistema);
		return usuario;
	}

	public String recuperarSenha(String login, EnumSistema sistema) {
		Usuario usuario = usuarioRepository.findByLogin(login).get();
		if (usuario == null) {
			throw new ExcecaoNegocio(String.format("Usu�rio de login %s inexistente", login));
		}

		// enviaEmail
		return enviadorEmailService.enviaEmailRecuperaSenha(usuario, sistema);
	}

}
