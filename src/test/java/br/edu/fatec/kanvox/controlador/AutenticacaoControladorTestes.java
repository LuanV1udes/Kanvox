package br.edu.fatec.kanvox.controlador;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Testes do fluxo de cadastro e login (RF-01.1). */
@SpringBootTest
@AutoConfigureMockMvc
class AutenticacaoControladorTestes {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void cadastroCriaUsuarioSemExporASenha() throws Exception {
		mockMvc.perform(post("/api/autenticacao/cadastro")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Aluno Teste\",\"email\":\"cadastro@teste.com\",\"senha\":\"123456\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.email").value("cadastro@teste.com"))
				// a senha (mesmo com hash) nunca pode aparecer na resposta (RNF-02)
				.andExpect(jsonPath("$.senha").doesNotExist());
	}

	@Test
	void cadastroComEmailRepetidoERejeitadoComMensagemAmigavel() throws Exception {
		String corpo = "{\"nome\":\"Aluno Teste\",\"email\":\"repetido@teste.com\",\"senha\":\"123456\"}";

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
				.content("{\"nome\":\"Aluno Teste\",\"email\":\"login@teste.com\",\"senha\":\"123456\"}"))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/autenticacao/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"login@teste.com\",\"senha\":\"123456\"}"))
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
				.content("{\"nome\":\"Aluno Eu\",\"email\":\"eu@teste.com\",\"senha\":\"123456\"}"))
				.andExpect(status().isCreated());
		String respostaLogin = mockMvc.perform(post("/api/autenticacao/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"eu@teste.com\",\"senha\":\"123456\"}"))
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
				.content("{\"nome\":\"Aluno Teste\",\"email\":\"rota404@teste.com\",\"senha\":\"123456\"}"))
				.andExpect(status().isCreated());

		String respostaLogin = mockMvc.perform(post("/api/autenticacao/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"rota404@teste.com\",\"senha\":\"123456\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String token = respostaLogin.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");

		mockMvc.perform(get("/api/rota-que-nao-existe")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.erro").value("Rota nao encontrada."));
	}

}
