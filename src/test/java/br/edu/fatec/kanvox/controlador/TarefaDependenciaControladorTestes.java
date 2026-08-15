package br.edu.fatec.kanvox.controlador;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

/**
 * Testes de dependencia entre tarefas: uma tarefa so sai de "A Fazer"
 * quando todas as suas dependencias estiverem concluidas, sem permitir
 * ciclos nem dependencias de outro projeto.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TarefaDependenciaControladorTestes {

	@Autowired
	private MockMvc mockMvc;

	private String cadastrarELogar(String nome, String email) throws Exception {
		mockMvc.perform(post("/api/autenticacao/cadastro")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"" + nome + "\",\"email\":\"" + email + "\",\"senha\":\"Senha123!\"}"));
		String resposta = mockMvc.perform(post("/api/autenticacao/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"senha\":\"Senha123!\"}"))
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(resposta, "$.token");
	}

	private long criarProjeto(String token, String nome) throws Exception {
		String resposta = mockMvc.perform(post("/api/projetos")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"" + nome + "\"}"))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(resposta, "$.id")).longValue();
	}

	private long criarTarefa(String token, long projetoId, String corpoJson) throws Exception {
		String resposta = mockMvc.perform(post("/api/projetos/" + projetoId + "/tarefas")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(corpoJson))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(resposta, "$.id")).longValue();
	}

	@Test
	void naoSaiDeAFazerComDependenciaPendenteEDepoisDeConcluidaFunciona() throws Exception {
		String token = cadastrarELogar("Gestor Dependencia", "gestor.dependencia@teste.com");
		long projetoId = criarProjeto(token, "Projeto Dependencia");

		long tarefaA = criarTarefa(token, projetoId, "{\"titulo\":\"Tarefa A\"}");
		long tarefaB = criarTarefa(token, projetoId,
				"{\"titulo\":\"Tarefa B\",\"dependencias\":[{\"id\":" + tarefaA + "}]}");

		// B nao pode sair de A Fazer enquanto A nao estiver concluida
		mockMvc.perform(put("/api/tarefas/" + tarefaB + "/status")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"EM_ANDAMENTO\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erro").value(containsString("Tarefa A")))
				.andExpect(jsonPath("$.erro").value(containsString("nao foi concluida")));

		// conclui A
		mockMvc.perform(put("/api/tarefas/" + tarefaA + "/status")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"CONCLUIDO\"}"))
				.andExpect(status().isOk());

		// agora B pode mover normalmente
		mockMvc.perform(put("/api/tarefas/" + tarefaB + "/status")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"EM_ANDAMENTO\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("EM_ANDAMENTO"));
	}

	@Test
	void umaTarefaNaoPodeDependerDelaMesma() throws Exception {
		String token = cadastrarELogar("Gestor Auto Dependencia", "gestor.autodependencia@teste.com");
		long projetoId = criarProjeto(token, "Projeto Auto Dependencia");
		long tarefaId = criarTarefa(token, projetoId, "{\"titulo\":\"Tarefa Sozinha\"}");

		mockMvc.perform(put("/api/tarefas/" + tarefaId)
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"Tarefa Sozinha\",\"dependencias\":[{\"id\":" + tarefaId + "}]}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erro").value("Uma tarefa nao pode depender dela mesma."));
	}

	@Test
	void naoPermiteCicloDeDependencias() throws Exception {
		String token = cadastrarELogar("Gestor Ciclo", "gestor.ciclo@teste.com");
		long projetoId = criarProjeto(token, "Projeto Ciclo");

		long tarefaA = criarTarefa(token, projetoId, "{\"titulo\":\"Tarefa A Ciclo\"}");
		long tarefaB = criarTarefa(token, projetoId,
				"{\"titulo\":\"Tarefa B Ciclo\",\"dependencias\":[{\"id\":" + tarefaA + "}]}");

		// B ja depende de A; fazer A depender de B fecharia um ciclo
		mockMvc.perform(put("/api/tarefas/" + tarefaA)
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"Tarefa A Ciclo\",\"dependencias\":[{\"id\":" + tarefaB + "}]}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erro").value(containsString("ciclo")));
	}

	@Test
	void dependenciaPrecisaSerDoMesmoProjeto() throws Exception {
		String token = cadastrarELogar("Gestor Cross Projeto", "gestor.crossprojeto@teste.com");
		long projeto1 = criarProjeto(token, "Projeto Um Dependencia");
		long projeto2 = criarProjeto(token, "Projeto Dois Dependencia");
		long tarefaDoProjeto1 = criarTarefa(token, projeto1, "{\"titulo\":\"Tarefa do projeto 1\"}");

		mockMvc.perform(post("/api/projetos/" + projeto2 + "/tarefas")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"Tarefa do projeto 2\",\"dependencias\":[{\"id\":" + tarefaDoProjeto1 + "}]}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erro").value("A dependencia precisa ser uma tarefa do mesmo projeto."));
	}

	@Test
	void excluirTarefaComDependentesLimpaAReferenciaSemQuebrar() throws Exception {
		String token = cadastrarELogar("Gestor Exclui Dependencia", "gestor.excluidependencia@teste.com");
		long projetoId = criarProjeto(token, "Projeto Exclui Dependencia");

		long tarefaA = criarTarefa(token, projetoId, "{\"titulo\":\"Tarefa A Excluir\"}");
		long tarefaB = criarTarefa(token, projetoId,
				"{\"titulo\":\"Tarefa B Excluir\",\"dependencias\":[{\"id\":" + tarefaA + "}]}");

		mockMvc.perform(delete("/api/tarefas/" + tarefaA)
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());

		// sem a dependencia (que foi excluida), B move normalmente
		mockMvc.perform(put("/api/tarefas/" + tarefaB + "/status")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"EM_ANDAMENTO\"}"))
				.andExpect(status().isOk());
	}

}
