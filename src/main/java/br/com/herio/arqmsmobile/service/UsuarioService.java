package br.com.herio.arqmsmobile.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import br.com.herio.arqmsmobile.dominio.ArquivoUsuario;
import br.com.herio.arqmsmobile.dominio.ArquivoUsuarioRepository;
import br.com.herio.arqmsmobile.dominio.Usuario;
import br.com.herio.arqmsmobile.dominio.UsuarioRepository;
import br.com.herio.arqmsmobile.dto.EnumSistema;
import br.com.herio.arqmsmobile.infra.drive.GoogleDriveFachada;
import br.com.herio.arqmsmobile.infra.excecao.ExcecaoNegocio;

@Service
public class UsuarioService {

	@Autowired
	protected UsuarioRepository usuarioRepository;

	@Autowired
	protected ArquivoUsuarioRepository arquivoUsuarioRepository;

	@Autowired
	protected AtivacaoUsuarioService ativacaoUsuarioService;

	@Autowired
	protected AutenticacaoService autenticacaoService;

	@Autowired
	protected EnviadorEmailService enviadorEmailService;

	@Autowired
	protected GoogleDriveFachada googleDriveFachada;

	@Autowired
	protected ConfiguracaoNotificacaoService configuracaoNotificacaoService;

	@Autowired
	private PrincipalService principalService;

	public Usuario criarUsuario(Usuario usuario) {
		if (usuario.getId() != null) {
			throw new IllegalArgumentException("Informe um novo usu�rio (sem id)!");
		}
		// verifica se usu�rio j� existe
		Optional<Usuario> usuarioOpt = usuarioRepository.findByLoginAndSistema(usuario.getLogin(), usuario.getSistema());
		Usuario usuarioBd = usuario;
		if (usuarioOpt.isPresent()) {
			usuarioBd = usuarioOpt.get();
			if (usuarioBd.getDataExclusao() == null) {
				// usu�rio existente
				throw new ExcecaoNegocio("Usu�rio j� cadastrado! Solicite a recupera��o de senha.");
			} else {
				// usu�rio exclu�do anteriormente, atualiza
				usuarioBd.setDataExclusao(null);
				atualizaUsuario(usuarioBd, usuario);
			}
		} else {
			usuarioBd.setSenha(Base64.getEncoder().encodeToString(usuario.getSenha().getBytes()));
		}

		// cria/atualiza exclu�do
		usuarioBd = usuarioRepository.save(usuarioBd);

		// atualiza token
		usuarioBd.setToken(autenticacaoService.criaTokenJwt(usuarioBd));

		// gera ativa��o
		ativacaoUsuarioService.gerarAtivacaoUsuario(usuario.getId());

		// criaConfiguracaoNotificacaoDefault
		EnumSistema sistema = EnumSistema.valueOf(usuario.getSistema());
		configuracaoNotificacaoService.criaConfiguracaoNotificacaoDefault(usuario.getId(), sistema);

		// enviaEmail
		enviadorEmailService.enviaEmailBoasVindas(usuario, sistema);
		return usuario;
	}

	public Usuario atualizarUsuario(Long idUsuario, Usuario usuario) {
		// somente usu�rio e admin podem realizar essa opera��o
		principalService.validaPermissaoUsuario(idUsuario);

		// atualiza
		Usuario usuarioBd = usuarioRepository.findById(idUsuario).get();
		atualizaUsuario(usuarioBd, usuario);
		usuarioBd = usuarioRepository.save(usuarioBd);
		usuarioBd.setToken(autenticacaoService.criaTokenJwt(usuarioBd));

		// enviaEmail
		enviadorEmailService.enviaEmailAtualizacaoDados(usuarioBd);
		return usuarioBd;
	}

	public boolean tornarAdmin(Long idUsuario) {
		// somente usu�rio e admin podem realizar essa opera��o
		principalService.validaPermissaoUsuario(idUsuario);

		Usuario usuarioBd = usuarioRepository.findById(idUsuario).get();
		usuarioBd.setAdmin(true);
		usuarioRepository.save(usuarioBd);
		return true;
	}

	public String recuperarSenha(String email, EnumSistema sistema) {
		Optional<Usuario> usuarioOpt = usuarioRepository.findByEmailAndSistema(email, sistema.name());
		if (!usuarioOpt.isPresent()) {
			throw new ExcecaoNegocio(String.format("Usu�rio de email '%s' inexistente!", email));
		}
		Usuario usuario = usuarioOpt.get();
		if (!usuario.isAtivado()) {
			throw new ExcecaoNegocio(String.format("Usu�rio '%s' n�o est� ativado!", usuario.getLogin()));
		}

		// enviaEmail
		return enviadorEmailService.enviaEmailRecuperaSenha(usuario, sistema);
	}

	public void removerUsuario(Long idUsuario) {
		// somente usu�rio e admin podem realizar essa opera��o
		principalService.validaPermissaoUsuario(idUsuario);

		Usuario usuario = usuarioRepository.findById(idUsuario).get();
		usuario.setAtivado(false);
		usuario.setDataExclusao(LocalDateTime.now(ZoneId.of("UTC-3")));
		usuarioRepository.save(usuario);
	}

	public Collection<Usuario> listarUsuarios(boolean ativos) {
		// somente admin pode realizar essa opera��o
		principalService.validaPermissaoUsuario(null);

		Stream<Usuario> stream = StreamSupport.stream(usuarioRepository.findAll().spliterator(), false);
		if (ativos) {
			stream = stream.filter(usuario -> usuario.isAtivado());
		}
		return stream.collect(Collectors.toList());
	}

	public java.io.File downloadFoto(Long idUsuario, boolean thumb) {
		Usuario usuario = usuarioRepository.findById(idUsuario).get();
		return googleDriveFachada.downloadFile(thumb ? usuario.getIdDriveFotoThumb() : usuario.getIdDriveFoto(), "foto.jpg");
	}

	public Usuario uploadFoto(Long idUsuario, MultipartFile mfile) {
		// somente usu�rio e admin podem realizar essa opera��o
		principalService.validaPermissaoUsuario(idUsuario);

		Usuario usuario = usuarioRepository.findById(idUsuario).get();
		EnumSistema sistema = EnumSistema.valueOf(usuario.getSistema());

		// upload
		googleDriveFachada.uploadFile(idUsuario, mfile, sistema.getUploadFolder(), usuario, sistema);

		usuarioRepository.save(usuario);

		// atualiza token
		usuario.setToken(autenticacaoService.criaTokenJwt(usuario));

		// enviaEmail
		enviadorEmailService.enviaEmailAtualizacaoDados(usuario);
		return usuario;
	}

	public boolean deleteFoto(Long idUsuario) {
		// somente usu�rio e admin podem realizar essa opera��o
		principalService.validaPermissaoUsuario(idUsuario);

		boolean removeu = false;
		Usuario usuario = usuarioRepository.findById(idUsuario).get();
		if (usuario.getIdDriveFoto() != null) {
			removeu = googleDriveFachada.deleteFile(usuario.getIdDriveFoto());
			if (usuario.getIdDriveFotoThumb() != null) {
				removeu = googleDriveFachada.deleteFile(usuario.getIdDriveFotoThumb());
			}
			if (removeu) {
				usuario.setUrlFoto(null);
				usuario.setUrlFotoThumb(null);
				usuarioRepository.save(usuario);
			}
		}
		return removeu;
	}

	public ArquivoUsuario uploadArquivo(Long idUsuario, MultipartFile mfile, String atributos) {
		// somente usu�rio e admin podem realizar essa opera��o
		principalService.validaPermissaoUsuario(idUsuario);

		Usuario usuario = usuarioRepository.findById(idUsuario).get();
		EnumSistema sistema = EnumSistema.valueOf(usuario.getSistema());
		ArquivoUsuario arquivo = new ArquivoUsuario();
		arquivo.setUsuario(usuario);
		arquivo.setAtributos(atributos);

		// upload
		googleDriveFachada.uploadFile(idUsuario, mfile, sistema.getUploadFolder(), arquivo, sistema);

		return arquivoUsuarioRepository.save(arquivo);
	}

	public java.io.File downloadArquivo(String idDrive, boolean thumb) {
		ArquivoUsuario arquivo = arquivoUsuarioRepository.findByIdDrive(idDrive).get();
		return googleDriveFachada.downloadFile(thumb ? arquivo.getIdDriveThumb() : idDrive, arquivo.getNome());
	}

	public boolean deleteArquivo(Long idUsuario, String idDrive) {
		// somente usu�rio e admin podem realizar essa opera��o
		principalService.validaPermissaoUsuario(idUsuario);

		ArquivoUsuario arquivo = arquivoUsuarioRepository.findByIdDrive(idDrive).get();
		if (!arquivo.getUsuario().getId().equals(idUsuario)) {
			throw new ExcecaoNegocio("Apenas o pr�prio Usu�rio pode remover esse arquivo!");
		}
		boolean removeu = googleDriveFachada.deleteFile(arquivo.getIdDrive());
		if (removeu) {
			if (arquivo.getIdDriveThumb() != null) {
				removeu = googleDriveFachada.deleteFile(arquivo.getIdDriveThumb());
			}
			arquivoUsuarioRepository.delete(arquivo);
		}
		return removeu;
	}

	public Collection<ArquivoUsuario> recuperaArquivosComAtributos(Long idUsuario, String atributos) {
		return arquivoUsuarioRepository.findAllByUsuarioIdAndAtributosContaining(idUsuario, atributos);
	}

	private void atualizaUsuario(Usuario usuarioBd, Usuario usuario) {
		usuarioBd.setSistema(usuario.getSistema());
		usuarioBd.setLogin(usuario.getLogin());
		usuarioBd.setEmail(usuario.getEmail());
		usuarioBd.setNome(usuario.getNome());
		usuarioBd.setSenha(Base64.getEncoder().encodeToString(usuario.getSenha().getBytes()));
		usuarioBd.setAtivado(usuario.isAtivado());
		usuarioBd.setAdmin(usuario.isAdmin());

		usuarioBd.setTelefone(usuario.getTelefone());
		usuarioBd.setCelular(usuario.getCelular());
		usuarioBd.setInstagram(usuario.getInstagram());
		usuarioBd.setFacebook(usuario.getFacebook());
		usuarioBd.setCpf(usuario.getCpf());
	}

}
