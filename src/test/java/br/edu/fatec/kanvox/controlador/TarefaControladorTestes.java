package br.edu.fatec.kanvox.controlador;

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
 * Testes do quadro Kanban (RF-03) — modulo com cobertura obrigatoria
 * segundo o documento do projeto. O foco e nas regras de permissao:
 * Gestor tudo, Membro so as proprias tarefas, Observador so leitura.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TarefaControladorTestes {

	@Autowired
	private MockMvc mockMvc;

	/** Cadastra um usuario e devolve o id gerado. */
	private long cadastrar(String nome, String email) throws Exception {
		String resposta = mockMvc.perform(post("/api/autenticacao/cadastro")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"" + nome + "\",\"email\":\"" + email + "\",\"senha\":\"123456\"}"))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(resposta, "$.id")).longValue();
	}

	private String logar(String email) throws Exception {
		String resposta = mockMvc.perform(post("/api/autenticacao/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"senha\":\"123456\"}"))
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(resposta, "$.token");
	}

	private long criarProjeto(String token, String nome) throws Exception {
		String resposta = mockMvc.perform(post("/api/projetos")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"" + nome + "\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(resposta, "$.id")).longValue();
	}

	private void convidar(String tokenGestor, long projetoId, String email, String papel) throws Exception {
		mockMvc.perform(post("/api/projetos/" + projetoId + "/membros")
				.header("Authorization", "Bearer " + tokenGestor)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"papel\":\"" + papel + "\"}"))
				.andExpect(status().isCreated());
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
	void gestorCriaTarefaParaMembroETarefaNasceEmAFazer() throws Exception {
		cadastrar("Gestor Kanban", "gestor.kanban@teste.com");
		long membroId = cadastrar("Membro Kanban", "membro.kanban@teste.com");
		String tokenGestor = logar("gestor.kanban@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto Kanban");
		convidar(tokenGestor, projetoId, "membro.kanban@teste.com", "MEMBRO");

		mockMvc.perform(post("/api/projetos/" + projetoId + "/tarefas")
				.header("Authorization", "Bearer " + tokenGestor)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"Configurar banco\",\"descricao\":\"Criar tabelas\",\"prazo\":\"2026-08-01\",\"responsavel\":{\"id\":" + membroId + "}}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("A_FAZER"))
				.andExpect(jsonPath("$.responsavel.email").value("membro.kanban@teste.com"));

		// o polling do quadro lista a tarefa (RF-03.6)
		mockMvc.perform(get("/api/projetos/" + projetoId + "/tarefas")
				.header("Authorization", "Bearer " + tokenGestor))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].titulo").value("Configurar banco"));
	}

	@Test
	void membroNaoPodeCriarTarefas() throws Exception {
		cadastrar("Gestor Auto", "gestor.auto@teste.com");
		cadastrar("Membro Auto", "membro.auto@teste.com");
		String tokenGestor = logar("gestor.auto@teste.com");
		String tokenMembro = logar("membro.auto@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto Criacao Restrita");
		convidar(tokenGestor, projetoId, "membro.auto@teste.com", "MEMBRO");

		// criar tarefas e exclusivo do Gestor (RF-03.2)
		mockMvc.perform(post("/api/projetos/" + projetoId + "/tarefas")
				.header("Authorization", "Bearer " + tokenMembro)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"Tarefa indevida\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.erro").value("Somente o Gestor do projeto pode criar tarefas."));
	}

	@Test
	void observadorTemAcessoSomenteLeitura() throws Exception {
		cadastrar("Gestor Obs", "gestor.obs@teste.com");
		cadastrar("Observador", "observador.obs@teste.com");
		String tokenGestor = logar("gestor.obs@teste.com");
		String tokenObservador = logar("observador.obs@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto Observado");
		convidar(tokenGestor, projetoId, "observador.obs@teste.com", "OBSERVADOR");
		long tarefaId = criarTarefa(tokenGestor, projetoId, "{\"titulo\":\"Tarefa visivel\"}");

		// observador ve o quadro normalmente
		mockMvc.perform(get("/api/projetos/" + projetoId + "/tarefas")
				.header("Authorization", "Bearer " + tokenObservador))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));

		// mas nao cria nem move tarefas
		mockMvc.perform(post("/api/projetos/" + projetoId + "/tarefas")
				.header("Authorization", "Bearer " + tokenObservador)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"Nao deveria existir\"}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(put("/api/tarefas/" + tarefaId + "/status")
				.header("Authorization", "Bearer " + tokenObservador)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"EM_ANDAMENTO\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void membroSoAlteraTarefasAtribuidasAEle() throws Exception {
		long gestorId = cadastrar("Gestor Dono", "gestor.dono@teste.com");
		long membroId = cadastrar("Membro Dono", "membro.dono@teste.com");
		String tokenGestor = logar("gestor.dono@teste.com");
		String tokenMembro = logar("membro.dono@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto Dono da Tarefa");
		convidar(tokenGestor, projetoId, "membro.dono@teste.com", "MEMBRO");

		// tarefa do gestor: membro nao pode editar nem mover
		long tarefaDoGestor = criarTarefa(tokenGestor, projetoId,
				"{\"titulo\":\"Tarefa do gestor\",\"responsavel\":{\"id\":" + gestorId + "}}");
		mockMvc.perform(put("/api/tarefas/" + tarefaDoGestor)
				.header("Authorization", "Bearer " + tokenMembro)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"Invasao\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.erro").value("Um Membro so pode alterar tarefas atribuidas a ele."));

		// tarefa atribuida a ele pelo gestor: membro edita e move normalmente
		long tarefaDoMembro = criarTarefa(tokenGestor, projetoId,
				"{\"titulo\":\"Tarefa do membro\",\"responsavel\":{\"id\":" + membroId + "}}");
		mockMvc.perform(put("/api/tarefas/" + tarefaDoMembro)
				.header("Authorization", "Bearer " + tokenMembro)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"Tarefa do membro atualizada\",\"prazo\":\"2026-09-15\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.titulo").value("Tarefa do membro atualizada"))
				.andExpect(jsonPath("$.prazo").value("2026-09-15"));
		mockMvc.perform(put("/api/tarefas/" + tarefaDoMembro + "/status")
				.header("Authorization", "Bearer " + tokenMembro)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"EM_ANDAMENTO\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("EM_ANDAMENTO"));
	}

	@Test
	void moverParaStatusInvalidoERejeitado() throws Exception {
		cadastrar("Gestor Status", "gestor.status@teste.com");
		String token = logar("gestor.status@teste.com");
		long projetoId = criarProjeto(token, "Projeto Status");
		long tarefaId = criarTarefa(token, projetoId, "{\"titulo\":\"Tarefa movel\"}");

		mockMvc.perform(put("/api/tarefas/" + tarefaId + "/status")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"COLUNA_INEXISTENTE\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erro").value("Status invalido. Use A_FAZER, EM_ANDAMENTO, BLOQUEADO ou CONCLUIDO."));
	}

	@Test
	void somenteGestorExcluiTarefas() throws Exception {
		cadastrar("Gestor Exclui", "gestor.exclui@teste.com");
		long membroId = cadastrar("Membro Exclui", "membro.exclui@teste.com");
		String tokenGestor = logar("gestor.exclui@teste.com");
		String tokenMembro = logar("membro.exclui@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto Exclusao");
		convidar(tokenGestor, projetoId, "membro.exclui@teste.com", "MEMBRO");

		// nem mesmo a propria tarefa o membro pode excluir (RF-03.2)
		long tarefaDoMembro = criarTarefa(tokenGestor, projetoId,
				"{\"titulo\":\"Tarefa a excluir\",\"responsavel\":{\"id\":" + membroId + "}}");
		mockMvc.perform(delete("/api/tarefas/" + tarefaDoMembro)
				.header("Authorization", "Bearer " + tokenMembro))
				.andExpect(status().isForbidden());

		mockMvc.perform(delete("/api/tarefas/" + tarefaDoMembro)
				.header("Authorization", "Bearer " + tokenGestor))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/projetos/" + projetoId + "/tarefas")
				.header("Authorization", "Bearer " + tokenGestor))
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void responsavelPrecisaSerMembroDoProjeto() throws Exception {
		cadastrar("Gestor Valida", "gestor.valida@teste.com");
		long usuarioDeForaId = cadastrar("Usuario De Fora", "fora.valida@teste.com");
		String tokenGestor = logar("gestor.valida@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto Validacao");

		mockMvc.perform(post("/api/projetos/" + projetoId + "/tarefas")
				.header("Authorization", "Bearer " + tokenGestor)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"Tarefa orfã\",\"responsavel\":{\"id\":" + usuarioDeForaId + "}}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erro").value("O responsavel precisa ser membro ativo do projeto."));
	}

	@Test
	void projetoEncerradoPermiteLeituraMasNaoEscrita() throws Exception {
		cadastrar("Gestor Fim", "gestor.fim@teste.com");
		String token = logar("gestor.fim@teste.com");
		long projetoId = criarProjeto(token, "Projeto Finalizado");
		long tarefaId = criarTarefa(token, projetoId, "{\"titulo\":\"Ultima tarefa\"}");

		mockMvc.perform(put("/api/projetos/" + projetoId + "/encerrar")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());

		// leitura continua funcionando
		mockMvc.perform(get("/api/projetos/" + projetoId + "/tarefas")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));

		// escrita e bloqueada
		mockMvc.perform(post("/api/projetos/" + projetoId + "/tarefas")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"Tarefa tardia\"}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(put("/api/tarefas/" + tarefaId + "/status")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"CONCLUIDO\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erro").value("O projeto esta encerrado: as tarefas nao podem mais ser alteradas."));
	}

}
