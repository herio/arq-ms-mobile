package br.com.herio.arqmsmobile.rest;

import java.util.Collection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.herio.arqmsmobile.dominio.Usuario;
import br.com.herio.arqmsmobile.dominio.UsuarioRepository;
import br.com.herio.arqmsmobile.service.UsuarioService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@Api("UsuariosController")
@RestController
@RequestMapping("/usuarios")
public class UsuariosController {

	@Autowired
	protected UsuarioRepository usuarioRepository;

	@Autowired
	protected UsuarioService usuarioService;

	@ApiOperation("atualizarUsuario")
	@PostMapping("/{idUsuario}")
	public Usuario atualizarUsuario(@PathVariable Long idUsuario, @RequestBody Usuario usuario) {
		if (idUsuario == null) {
			throw new IllegalArgumentException("Informe um usu�rio v�lido!");
		}
		return usuarioService.atualizarUsuario(idUsuario, usuario);
	}

	@ApiOperation("removerUsuario")
	@DeleteMapping("/{idUsuario}")
	public void removerUsuario(@PathVariable Long idUsuario) {
		if (idUsuario == null) {
			throw new IllegalArgumentException("Informe um usu�rio v�lido!");
		}
		usuarioService.removerUsuario(idUsuario);
	}

	@ApiOperation("listarUsuarios")
	@GetMapping
	public Collection<Usuario> listarUsuarios(@RequestParam(required = false) boolean ativos) {
		return usuarioService.listarUsuarios(ativos);
	}

}
