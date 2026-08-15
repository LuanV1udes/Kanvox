package br.edu.fatec.kanvox.controlador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import br.edu.fatec.kanvox.modelo.Tarefa;
import br.edu.fatec.kanvox.repositorio.TarefaRepositorio;

/**
 * Testes do painel de desempenho: carga de trabalho e eficiencia de entrega
 * da equipe, calculados em cima de Tarefa (responsaveis, status, prazo,
 * criadoEm, concluidaEm) — exclusivo do Gestor.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DesempenhoControladorTestes {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TarefaRepositorio tarefaRepositorio;

	private long cadastrar(String nome, String email) throws Exception {
		String resposta = mockMvc.perform(post("/api/autenticacao/cadastro")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"" + nome + "\",\"email\":\"" + email + "\",\"senha\":\"Senha123!\"}"))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(resposta, "$.id")).longValue();
	}

	private String logar(String email) throws Exception {
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

	private void convidarEAceitar(String tokenGestor, long projetoId, String email, String tokenConvidado) throws Exception {
		mockMvc.perform(post("/api/projetos/" + projetoId + "/membros")
				.header("Authorization", "Bearer " + tokenGestor)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"papel\":\"MEMBRO\"}"))
				.andExpect(status().isCreated());
		String convites = mockMvc.perform(get("/api/convites")
				.header("Authorization", "Bearer " + tokenConvidado))
				.andReturn().getResponse().getContentAsString();
		long conviteId = ((Number) JsonPath.read(convites, "$[0].id")).longValue();
		mockMvc.perform(post("/api/convites/" + conviteId + "/aceitar")
				.header("Authorization", "Bearer " + tokenConvidado))
				.andExpect(status().isOk());
	}

	private long criarTarefa(String tokenGestor, long projetoId, String corpoJson) throws Exception {
		String resposta = mockMvc.perform(post("/api/projetos/" + projetoId + "/tarefas")
				.header("Authorization", "Bearer " + tokenGestor)
				.contentType(MediaType.APPLICATION_JSON)
				.content(corpoJson))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(resposta, "$.id")).longValue();
	}

	/** Acha a linha do painel de desempenho referente a um membro, pelo e-mail. */
	private Map<String, Object> buscarLinhaDoMembro(String respostaDoDesempenho, String email) {
		List<Map<String, Object>> porMembro = JsonPath.read(respostaDoDesempenho, "$.porMembro");
		return porMembro.stream()
				.filter(linha -> email.equals(((Map<?, ?>) linha.get("usuario")).get("email")))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Membro nao encontrado no painel: " + email));
	}

	@Test
	void somenteGestorAcessaODesempenho() throws Exception {
		cadastrar("Gestor Desempenho Rbac", "gestor.desempenhorbac@teste.com");
		cadastrar("Membro Desempenho Rbac", "membro.desempenhorbac@teste.com");
		String tokenGestor = logar("gestor.desempenhorbac@teste.com");
		String tokenMembro = logar("membro.desempenhorbac@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto Desempenho Rbac");
		convidarEAceitar(tokenGestor, projetoId, "membro.desempenhorbac@teste.com", tokenMembro);

		mockMvc.perform(get("/api/projetos/" + projetoId + "/desempenho")
				.header("Authorization", "Bearer " + tokenMembro))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/projetos/" + projetoId + "/desempenho")
				.header("Authorization", "Bearer " + tokenGestor))
				.andExpect(status().isOk());
	}

	@Test
	void contadoresPorMembroRefletemAsTarefasAtribuidas() throws Exception {
		long membroId = cadastrar("Membro Contadores", "membro.contadores@teste.com");
		cadastrar("Gestor Contadores", "gestor.contadores@teste.com");
		String tokenGestor = logar("gestor.contadores@teste.com");
		String tokenMembro = logar("membro.contadores@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto Contadores Desempenho");
		convidarEAceitar(tokenGestor, projetoId, "membro.contadores@teste.com", tokenMembro);

		// 1 concluida
		long concluida = criarTarefa(tokenGestor, projetoId,
				"{\"titulo\":\"Tarefa concluida\",\"responsaveis\":[{\"id\":" + membroId + "}]}");
		mockMvc.perform(put("/api/tarefas/" + concluida + "/status")
				.header("Authorization", "Bearer " + tokenGestor)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"CONCLUIDO\"}"))
				.andExpect(status().isOk());

		// 1 aberta com prazo no futuro (nao conta como atrasada)
		criarTarefa(tokenGestor, projetoId,
				"{\"titulo\":\"Tarefa em dia\",\"prazo\":\"" + LocalDate.now().plusDays(10)
						+ "\",\"responsaveis\":[{\"id\":" + membroId + "}]}");

		// 1 aberta com prazo vencido (atrasada)
		criarTarefa(tokenGestor, projetoId,
				"{\"titulo\":\"Tarefa atrasada\",\"prazo\":\"" + LocalDate.now().minusDays(1)
						+ "\",\"responsaveis\":[{\"id\":" + membroId + "}]}");

		String resposta = mockMvc.perform(get("/api/projetos/" + projetoId + "/desempenho")
				.header("Authorization", "Bearer " + tokenGestor))
				.andExpect(status().isOk())
				// resumo do time: 1 de 3 tarefas concluidas = 33% de progresso
				.andExpect(jsonPath("$.resumo.progresso").value(33))
				.andReturn().getResponse().getContentAsString();

		Map<String, Object> linha = buscarLinhaDoMembro(resposta, "membro.contadores@teste.com");
		assertEquals(1, ((Number) linha.get("concluidas")).intValue());
		assertEquals(2, ((Number) linha.get("emAberto")).intValue());
		assertEquals(1, ((Number) linha.get("atrasadas")).intValue());
	}

	@Test
	void calculaTempoMedioDeConclusaoEEficienciaDeEntrega() throws Exception {
		long membroId = cadastrar("Membro Eficiencia", "membro.eficiencia@teste.com");
		cadastrar("Gestor Eficiencia", "gestor.eficiencia@teste.com");
		String tokenGestor = logar("gestor.eficiencia@teste.com");
		String tokenMembro = logar("membro.eficiencia@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto Eficiencia");
		convidarEAceitar(tokenGestor, projetoId, "membro.eficiencia@teste.com", tokenMembro);

		long tarefaId = criarTarefa(tokenGestor, projetoId,
				"{\"titulo\":\"Tarefa cronometrada\",\"responsaveis\":[{\"id\":" + membroId + "}]}");
		mockMvc.perform(put("/api/tarefas/" + tarefaId + "/status")
				.header("Authorization", "Bearer " + tokenGestor)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"CONCLUIDO\"}"))
				.andExpect(status().isOk());

		// prazo dado: 5 dias a partir da criacao; entrega real: 2 dias -> eficiencia = +3 dias
		LocalDateTime criadoEm = LocalDateTime.of(2026, 1, 1, 10, 0);
		Tarefa tarefa = tarefaRepositorio.findById(tarefaId).orElseThrow();
		tarefa.setCriadoEm(criadoEm);
		tarefa.setPrazo(criadoEm.toLocalDate().plusDays(5));
		tarefa.setConcluidaEm(criadoEm.plusDays(2));
		tarefaRepositorio.save(tarefa);

		String resposta = mockMvc.perform(get("/api/projetos/" + projetoId + "/desempenho")
				.header("Authorization", "Bearer " + tokenGestor))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		Map<String, Object> linha = buscarLinhaDoMembro(resposta, "membro.eficiencia@teste.com");
		assertEquals(2.0, ((Number) linha.get("tempoMedioDeConclusaoEmDias")).doubleValue());
		assertEquals(3.0, ((Number) linha.get("eficienciaMediaEmDias")).doubleValue());
	}

	@Test
	void membroSemTarefasApareceComContadoresZeradosENulos() throws Exception {
		cadastrar("Gestor Sem Tarefas", "gestor.semtarefas@teste.com");
		cadastrar("Membro Sem Tarefas", "membro.semtarefas@teste.com");
		String tokenGestor = logar("gestor.semtarefas@teste.com");
		String tokenMembro = logar("membro.semtarefas@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto Sem Tarefas");
		convidarEAceitar(tokenGestor, projetoId, "membro.semtarefas@teste.com", tokenMembro);

		String resposta = mockMvc.perform(get("/api/projetos/" + projetoId + "/desempenho")
				.header("Authorization", "Bearer " + tokenGestor))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		Map<String, Object> linha = buscarLinhaDoMembro(resposta, "membro.semtarefas@teste.com");
		assertEquals(0, ((Number) linha.get("concluidas")).intValue());
		assertEquals(0, ((Number) linha.get("emAberto")).intValue());
		assertEquals(0, ((Number) linha.get("atrasadas")).intValue());
		assertNull(linha.get("tempoMedioDeConclusaoEmDias"));
		assertNull(linha.get("eficienciaMediaEmDias"));
	}

}
