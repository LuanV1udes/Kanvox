package br.edu.fatec.kanvox.controlador;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.MockMvc;

import br.edu.fatec.kanvox.modelo.Usuario;
import br.edu.fatec.kanvox.repositorio.UsuarioRepositorio;
import jakarta.mail.Address;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/**
 * Testes do fluxo de cadastro, login e recuperacao de senha (RF-01.1 / RF-01.3).
 * O envio real de e-mail e substituido por uma implementacao falsa (abaixo),
 * que so guarda as mensagens em memoria — os testes nao dependem de SMTP.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(AutenticacaoControladorTestes.ConfiguracaoDeEmailFalso.class)
class AutenticacaoControladorTestes {

	@TestConfiguration
	static class ConfiguracaoDeEmailFalso {
		static final List<MimeMessage> MENSAGENS_ENVIADAS = new ArrayList<>();
		private static final Session SESSAO_DE_TESTE = Session.getInstance(new Properties());

		@Bean
		@Primary
		JavaMailSender javaMailSenderFalso() {
			JavaMailSender falso = Mockito.mock(JavaMailSender.class);
			// cada chamada devolve uma mensagem NOVA, igual a implementacao real faria
			Mockito.when(falso.createMimeMessage()).thenAnswer(invocacao -> new MimeMessage(SESSAO_DE_TESTE));
			Mockito.doAnswer(invocacao -> {
				MENSAGENS_ENVIADAS.add(invocacao.getArgument(0));
				return null;
			}).when(falso).send(Mockito.any(MimeMessage.class));
			return falso;
		}
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepositorio usuarioRepositorio;

	/** Extrai o token do link presente no corpo do e-mail mais recente enviado ao destinatario. */
	private String extrairTokenDoUltimoEmailPara(String destinatario) throws Exception {
		MimeMessage encontrada = null;
		for (MimeMessage mensagem : ConfiguracaoDeEmailFalso.MENSAGENS_ENVIADAS) {
			for (Address endereco : mensagem.getAllRecipients()) {
				if (endereco.toString().contains(destinatario)) {
					encontrada = mensagem;
				}
			}
		}
		if (encontrada == null) {
			throw new AssertionError("Nenhum e-mail enviado para " + destinatario);
		}

		// modo multipart=true aninha "multipart/alternative" (texto+html) dentro de um
		// "multipart/mixed" externo — desce recursivamente ate achar a parte com o link
		String textoComOLink = buscarTextoComToken(encontrada.getContent());
		if (textoComOLink == null) {
			throw new AssertionError("Link com token nao encontrado no corpo do e-mail.");
		}

		Matcher correspondencia = Pattern.compile("token=([\\w-]+)").matcher(textoComOLink);
		if (!correspondencia.find()) {
			throw new AssertionError("Link com token nao encontrado no corpo do e-mail.");
		}
		return correspondencia.group(1);
	}

	private String buscarTextoComToken(Object conteudo) throws Exception {
		if (conteudo instanceof String texto) {
			return texto.contains("token=") ? texto : null;
		}
		if (conteudo instanceof Multipart multipart) {
			for (int indice = 0; indice < multipart.getCount(); indice++) {
				String texto = buscarTextoComToken(multipart.getBodyPart(indice).getContent());
				if (texto != null) {
					return texto;
				}
			}
		}
		return null;
	}

	@Test
	void cadastroCriaUsuarioSemExporASenha() throws Exception {
		mockMvc.perform(post("/api/autenticacao/cadastro")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Aluno Teste\",\"email\":\"cadastro@teste.com\",\"senha\":\"Senha123!\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.email").value("cadastro@teste.com"))
				// a senha (mesmo com hash) nunca pode aparecer na resposta (RNF-02)
				.andExpect(jsonPath("$.senha").doesNotExist());
	}

	@Test
	void cadastroComSenhaCurtaERejeitado() throws Exception {
		mockMvc.perform(post("/api/autenticacao/cadastro")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Aluno Senha Curta\",\"email\":\"senhacurta@teste.com\",\"senha\":\"Abc1!\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erro").value("A senha deve ter pelo menos 8 caracteres."));
	}

	@Test
	void cadastroComSenhaSemVariedadeDeCaracteresERejeitado() throws Exception {
		// 8+ caracteres, mas so letra minuscula: atende 1 dos 4 criterios de variedade (RNF-02)
		mockMvc.perform(post("/api/autenticacao/cadastro")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Aluno Senha Fraca\",\"email\":\"senhafraca@teste.com\",\"senha\":\"somenteminuscula\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erro").value(
						"A senha e fraca demais. Combine pelo menos 3 destes: letra maiuscula, letra minuscula, numero e simbolo."));
	}

	@Test
	void cadastroComSenhaForteFunciona() throws Exception {
		// 8+ caracteres com maiuscula, minuscula e numero: 3 dos 4 criterios, ja e suficiente
		mockMvc.perform(post("/api/autenticacao/cadastro")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Aluno Senha Forte\",\"email\":\"senhaforte@teste.com\",\"senha\":\"Abcdefg1\"}"))
				.andExpect(status().isCreated());
	}

	@Test
	void cadastroComEmailRepetidoERejeitadoComMensagemAmigavel() throws Exception {
		String corpo = "{\"nome\":\"Aluno Teste\",\"email\":\"repetido@teste.com\",\"senha\":\"Senha123!\"}";

		mockMvc.perform(post("/api/autenticacao/cadastro")
				.contentType(MediaType.APPLICATION_JSON).content(corpo))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/autenticacao/cadastro")
				.contentType(MediaType.APPLICATION_JSON).content(corpo))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erro").value("Ja existe um usuario cadastrado com este e-mail."));
	}

	@Test
	void loginComCredenciaisCorretasDevolveToken() throws Exception {
		mockMvc.perform(post("/api/autenticacao/cadastro")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Aluno Teste\",\"email\":\"login@teste.com\",\"senha\":\"Senha123!\"}"))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/autenticacao/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"login@teste.com\",\"senha\":\"Senha123!\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty());
	}

	@Test
	void loginComSenhaErradaERejeitado() throws Exception {
		mockMvc.perform(post("/api/autenticacao/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"nao-existe@teste.com\",\"senha\":\"errada\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erro").value("E-mail ou senha invalidos."));
	}

	@Test
	void rotaProtegidaSemTokenERecusada() throws Exception {
		mockMvc.perform(get("/api/projetos"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void rotaEuDevolveOUsuarioLogado() throws Exception {
		mockMvc.perform(post("/api/autenticacao/cadastro")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Aluno Eu\",\"email\":\"eu@teste.com\",\"senha\":\"Senha123!\"}"))
				.andExpect(status().isCreated());
		String respostaLogin = mockMvc.perform(post("/api/autenticacao/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"eu@teste.com\",\"senha\":\"Senha123!\"}"))
				.andReturn().getResponse().getContentAsString();
		String token = respostaLogin.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");

		// com token devolve os dados do usuario (sem a senha)
		mockMvc.perform(get("/api/autenticacao/eu")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("eu@teste.com"))
				.andExpect(jsonPath("$.senha").doesNotExist());

		// sem token e recusado
		mockMvc.perform(get("/api/autenticacao/eu"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void rotaInexistenteComTokenValidoDevolve404() throws Exception {
		mockMvc.perform(post("/api/autenticacao/cadastro")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Aluno Teste\",\"email\":\"rota404@teste.com\",\"senha\":\"Senha123!\"}"))
				.andExpect(status().isCreated());

		String respostaLogin = mockMvc.perform(post("/api/autenticacao/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"rota404@teste.com\",\"senha\":\"Senha123!\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String token = respostaLogin.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");

		mockMvc.perform(get("/api/rota-que-nao-existe")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.erro").value("Rota nao encontrada."));
	}

	@Test
	void fluxoCompletoDeRecuperacaoDeSenhaFunciona() throws Exception {
		mockMvc.perform(post("/api/autenticacao/cadastro")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Aluno Recupera\",\"email\":\"recupera@teste.com\",\"senha\":\"SenhaAntiga1!\"}"))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/autenticacao/esqueci-senha")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"recupera@teste.com\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mensagem").value("Se este e-mail estiver cadastrado, enviamos um link de redefinição."));

		String token = extrairTokenDoUltimoEmailPara("recupera@teste.com");

		// a tela de redefinicao consulta o e-mail da conta antes de pedir a nova senha
		mockMvc.perform(get("/api/autenticacao/token-recuperacao/" + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("recupera@teste.com"));

		mockMvc.perform(post("/api/autenticacao/redefinir-senha")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + token + "\",\"novaSenha\":\"senhaNova123\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mensagem").value("Senha redefinida com sucesso. Você já pode entrar."));

		// a senha antiga nao funciona mais
		mockMvc.perform(post("/api/autenticacao/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"recupera@teste.com\",\"senha\":\"SenhaAntiga1!\"}"))
				.andExpect(status().isBadRequest());

		// a senha nova funciona
		mockMvc.perform(post("/api/autenticacao/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"recupera@teste.com\",\"senha\":\"senhaNova123\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty());

		// o mesmo token nao pode ser reutilizado — vale uma unica vez (RF-01.3)
		mockMvc.perform(post("/api/autenticacao/redefinir-senha")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + token + "\",\"novaSenha\":\"outraSenha123\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erro").value("Link de redefinicao invalido ou ja utilizado."));
	}

	@Test
	void consultarTokenDeRecuperacaoInvalidoOuExpiradoERejeitado() throws Exception {
		mockMvc.perform(get("/api/autenticacao/token-recuperacao/token-que-nao-existe"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erro").value("Link de redefinicao invalido ou ja utilizado."));

		mockMvc.perform(post("/api/autenticacao/cadastro")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Aluno Consulta Expirada\",\"email\":\"consulta.expirada@teste.com\",\"senha\":\"Senha123!\"}"))
				.andExpect(status().isCreated());
		Usuario usuario = usuarioRepositorio.buscarPorEmail("consulta.expirada@teste.com").orElseThrow();
		usuario.setTokenRecuperacao("token-de-consulta-expirado");
		usuario.setTokenExpiraEm(LocalDateTime.now().minusMinutes(1));
		usuarioRepositorio.save(usuario);

		mockMvc.perform(get("/api/autenticacao/token-recuperacao/token-de-consulta-expirado"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erro").value("Link de redefinicao expirado. Solicite um novo."));
	}

	@Test
	void esqueciSenhaComEmailInexistenteRespondeGenericoSemQuebrar() throws Exception {
		// nao revela se o e-mail existe ou nao (evita enumeracao de contas, RNF-02)
		mockMvc.perform(post("/api/autenticacao/esqueci-senha")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"nao-cadastrado@teste.com\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mensagem").value("Se este e-mail estiver cadastrado, enviamos um link de redefinição."));
	}

	@Test
	void redefinirSenhaComTokenInvalidoERejeitado() throws Exception {
		mockMvc.perform(post("/api/autenticacao/redefinir-senha")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"token-que-nao-existe\",\"novaSenha\":\"123456\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erro").value("Link de redefinicao invalido ou ja utilizado."));
	}

	@Test
	void redefinirSenhaComTokenExpiradoERejeitado() throws Exception {
		mockMvc.perform(post("/api/autenticacao/cadastro")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Aluno Expirado\",\"email\":\"expirado@teste.com\",\"senha\":\"Senha123!\"}"))
				.andExpect(status().isCreated());

		// simula um token gerado ha mais de 1h (a passagem do tempo nao e testavel sem mexer no relogio)
		Usuario usuario = usuarioRepositorio.buscarPorEmail("expirado@teste.com").orElseThrow();
		usuario.setTokenRecuperacao("token-expirado-de-teste");
		usuario.setTokenExpiraEm(LocalDateTime.now().minusMinutes(1));
		usuarioRepositorio.save(usuario);

		mockMvc.perform(post("/api/autenticacao/redefinir-senha")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"token-expirado-de-teste\",\"novaSenha\":\"123456novo\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erro").value("Link de redefinicao expirado. Solicite um novo."));
	}

	@Test
	void redefinirSenhaComSenhaCurtaERejeitada() throws Exception {
		mockMvc.perform(post("/api/autenticacao/cadastro")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Aluno Curto\",\"email\":\"curto@teste.com\",\"senha\":\"Senha123!\"}"))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/autenticacao/esqueci-senha")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"curto@teste.com\"}"))
				.andExpect(status().isOk());
		String token = extrairTokenDoUltimoEmailPara("curto@teste.com");

		mockMvc.perform(post("/api/autenticacao/redefinir-senha")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + token + "\",\"novaSenha\":\"123\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erro").value("A nova senha deve ter pelo menos 6 caracteres."));
	}

	@Test
	void editarPerfilAtualizaONomeDoUsuario() throws Exception {
		mockMvc.perform(post("/api/autenticacao/cadastro")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Nome Antigo\",\"email\":\"perfil@teste.com\",\"senha\":\"Senha123!\"}"))
				.andExpect(status().isCreated());
		String respostaLogin = mockMvc.perform(post("/api/autenticacao/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"perfil@teste.com\",\"senha\":\"Senha123!\"}"))
				.andReturn().getResponse().getContentAsString();
		String token = respostaLogin.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");

		mockMvc.perform(put("/api/autenticacao/perfil")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Nome Novo\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nome").value("Nome Novo"))
				.andExpect(jsonPath("$.senha").doesNotExist());

		// a mudanca fica salva: consultar /eu de novo devolve o nome atualizado
		mockMvc.perform(get("/api/autenticacao/eu")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nome").value("Nome Novo"));
	}

	@Test
	void editarPerfilComNomeVazioERejeitado() throws Exception {
		mockMvc.perform(post("/api/autenticacao/cadastro")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Aluno Perfil Vazio\",\"email\":\"perfilvazio@teste.com\",\"senha\":\"Senha123!\"}"))
				.andExpect(status().isCreated());
		String respostaLogin = mockMvc.perform(post("/api/autenticacao/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"perfilvazio@teste.com\",\"senha\":\"Senha123!\"}"))
				.andReturn().getResponse().getContentAsString();
		String token = respostaLogin.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");

		mockMvc.perform(put("/api/autenticacao/perfil")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erro").value("O nome e obrigatorio."));
	}

	@Test
	void alterarSenhaExigeASenhaAtualCorretaEUmaSenhaNovaValida() throws Exception {
		mockMvc.perform(post("/api/autenticacao/cadastro")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Aluno Troca Senha\",\"email\":\"trocasenha@teste.com\",\"senha\":\"SenhaAntiga1!\"}"))
				.andExpect(status().isCreated());
		String respostaLogin = mockMvc.perform(post("/api/autenticacao/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"trocasenha@teste.com\",\"senha\":\"SenhaAntiga1!\"}"))
				.andReturn().getResponse().getContentAsString();
		String token = respostaLogin.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");

		// senha atual errada e rejeitada
		mockMvc.perform(put("/api/autenticacao/senha")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"senhaAtual\":\"errada\",\"novaSenha\":\"SenhaNova123!\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erro").value("Senha atual incorreta."));

		// senha nova curta demais e rejeitada, mesmo com a senha atual certa
		mockMvc.perform(put("/api/autenticacao/senha")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"senhaAtual\":\"SenhaAntiga1!\",\"novaSenha\":\"123\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erro").value("A senha deve ter pelo menos 8 caracteres."));

		// senha nova sem variedade de caracteres tambem e rejeitada (RNF-02): mesma regra do cadastro
		mockMvc.perform(put("/api/autenticacao/senha")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"senhaAtual\":\"SenhaAntiga1!\",\"novaSenha\":\"somenteminuscula\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erro").value(
						"A senha e fraca demais. Combine pelo menos 3 destes: letra maiuscula, letra minuscula, numero e simbolo."));

		// com senha atual certa e senha nova forte, a troca funciona
		mockMvc.perform(put("/api/autenticacao/senha")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"senhaAtual\":\"SenhaAntiga1!\",\"novaSenha\":\"SenhaNova123!\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mensagem").value("Senha alterada com sucesso."));

		// a senha antiga nao funciona mais, a nova sim
		mockMvc.perform(post("/api/autenticacao/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"trocasenha@teste.com\",\"senha\":\"SenhaAntiga1!\"}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/autenticacao/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"trocasenha@teste.com\",\"senha\":\"SenhaNova123!\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty());
	}

}
