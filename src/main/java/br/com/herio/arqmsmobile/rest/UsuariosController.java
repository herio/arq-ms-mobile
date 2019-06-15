package br.com.herio.arqmsmobile.rest;

import java.util.Base64;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import br.com.herio.arqmsmobile.dominio.Usuario;
import br.com.herio.arqmsmobile.dominio.UsuarioRepository;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@Api("UsuariosController")
@RestController
public class UsuariosController {
	
	@Autowired
	private UsuarioRepository usuarioRepository;

	@ApiOperation("criarUsuario")
	@PostMapping("/publico/usuarios")
	public Usuario criarUsuario(@RequestBody Usuario usuario) {
		if(usuario.getId() != null) {
			throw new IllegalArgumentException("Informe um novo usu�rio sem id!");
		}
		//cria
		usuario.valida();
		usuario.setSenha(Base64.getEncoder().encodeToString(usuario.getSenha().getBytes()));
		return usuarioRepository.save(usuario);
	}

	@ApiOperation("atualizarUsuario")
	@PostMapping("/usuarios")
	public Usuario atualizarUsuario(@RequestBody Usuario usuario) {
		if(usuario.getId() == null) {
			throw new IllegalArgumentException("Informe um usu�rio com id!");
		}
		//atualiza
		Usuario usuarioBd = usuarioRepository.findById(usuario.getId()).get();
		usuarioBd.setLogin(usuario.getLogin());
		usuarioBd.setNome(usuario.getNome());
		usuarioBd.setEmail(usuario.getEmail());
		usuarioBd.setUrlFoto(usuario.getUrlFoto());
		usuarioBd.valida();
		return usuarioRepository.save(usuarioBd);	
	}

	@ApiOperation("listarUsuarios")
	@GetMapping("/usuarios")
	public Collection<Usuario> listarUsuarios() {
		return StreamSupport.stream(usuarioRepository.findAll().spliterator(), false).collect(Collectors.toList());
	}
}
