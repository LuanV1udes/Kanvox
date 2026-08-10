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

/** Testes do CRUD de projetos e da gestao de membros com RBAC (RF-02 e RF-01). */
@SpringBootTest
@AutoConfigureMockMvc
class ProjetoControladorTestes {

	@Autowired
	private MockMvc mockMvc;

	/** Cadastra um usuario novo e devolve o token de login dele. */
	private String cadastrarELogar(String nome, String email) throws Exception {
		mockMvc.perform(post("/api/autenticacao/cadastro")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"" + nome + "\",\"email\":\"" + email + "\",\"senha\":\"123456\"}"));
		String resposta = mockMvc.perform(post("/api/autenticacao/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"senha\":\"123456\"}"))
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(resposta, "$.token");
	}

	/** Cria um projeto e devolve o id gerado. */
	private long criarProjeto(String token, String nome) throws Exception {
		String resposta = mockMvc.perform(post("/api/projetos")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"" + nome + "\",\"descricao\":\"Projeto de teste\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(resposta, "$.id")).longValue();
	}

	@Test
	void criarProjetoTornaOCriadorGestor() throws Exception {
		String token = cadastrarELogar("Gestora", "gestora@teste.com");
		long projetoId = criarProjeto(token, "Projeto Kanvox");

		mockMvc.perform(get("/api/projetos/" + projetoId + "/membros")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].usuario.email").value("gestora@teste.com"))
				.andExpect(jsonPath("$[0].papelNoProjeto").value("GESTOR"));
	}

	@Test
	void visaoGeralMostraProgressoEMembros() throws Exception {
		String token = cadastrarELogar("Gestor Visao", "visao@teste.com");
		long projetoId = criarProjeto(token, "Projeto Visao Geral");

		mockMvc.perform(get("/api/projetos/" + projetoId)
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.projeto.nome").value("Projeto Visao Geral"))
				.andExpect(jsonPath("$.progresso").value(0))
				.andExpect(jsonPath("$.totalTarefas").value(0))
				.andExpect(jsonPath("$.membros.length()").value(1));
	}

	@Test
	void convidarMembroCadastradoFunciona() throws Exception {
		String tokenGestor = cadastrarELogar("Gestor Convite", "gestor.convite@teste.com");
		String tokenMembro = cadastrarELogar("Membro Convidado", "membro.convite@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto Convite");

		mockMvc.perform(post("/api/projetos/" + projetoId + "/membros")
				.header("Authorization", "Bearer " + tokenGestor)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"membro.convite@teste.com\",\"papel\":\"MEMBRO\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.papelNoProjeto").value("MEMBRO"));

		// o convidado agora consegue ver o projeto
		mockMvc.perform(get("/api/projetos/" + projetoId)
				.header("Authorization", "Bearer " + tokenMembro))
				.andExpect(status().isOk());
	}

	@Test
	void convidarEmailSemCadastroERejeitado() throws Exception {
		String token = cadastrarELogar("Gestor Sozinho", "gestor.sozinho@teste.com");
		long projetoId = criarProjeto(token, "Projeto Sem Convidado");

		mockMvc.perform(post("/api/projetos/" + projetoId + "/membros")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"nao.existe@teste.com\",\"papel\":\"MEMBRO\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erro").value(
						"Nenhum usuario cadastrado com este e-mail. O convite so funciona para quem ja tem conta."));
	}

	@Test
	void membroNaoPodeEditarNemConvidar() throws Exception {
		String tokenGestor = cadastrarELogar("Gestor RBAC", "gestor.rbac@teste.com");
		String tokenMembro = cadastrarELogar("Membro RBAC", "membro.rbac@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto RBAC");

		mockMvc.perform(post("/api/projetos/" + projetoId + "/membros")
				.header("Authorization", "Bearer " + tokenGestor)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"membro.rbac@teste.com\",\"papel\":\"MEMBRO\"}"))
				.andExpect(status().isCreated());

		// membro tenta editar o projeto: 403
		mockMvc.perform(put("/api/projetos/" + projetoId)
				.header("Authorization", "Bearer " + tokenMembro)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Tentativa de edicao\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.erro").value("Somente o Gestor do projeto pode executar esta operacao."));

		// membro tenta convidar alguem: 403 (convite e exclusivo do Gestor, RF-01.4)
		String tokenTerceiro = cadastrarELogar("Terceiro RBAC", "terceiro.rbac@teste.com");
		mockMvc.perform(post("/api/projetos/" + projetoId + "/membros")
				.header("Authorization", "Bearer " + tokenMembro)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"terceiro.rbac@teste.com\",\"papel\":\"MEMBRO\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.erro").value("Somente o Gestor do projeto pode executar esta operacao."));

		// quem nem participa do projeto tambem recebe 403
		String tokenDeFora = cadastrarELogar("Fora do Projeto", "fora.rbac@teste.com");
		mockMvc.perform(get("/api/projetos/" + projetoId)
				.header("Authorization", "Bearer " + tokenDeFora))
				.andExpect(status().isForbidden());
	}

	@Test
	void membroPodeSairMasGestorNao() throws Exception {
		String tokenGestor = cadastrarELogar("Gestor Saida", "gestor.saida@teste.com");
		String tokenMembro = cadastrarELogar("Membro Saida", "membro.saida@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto Saida");

		mockMvc.perform(post("/api/projetos/" + projetoId + "/membros")
				.header("Authorization", "Bearer " + tokenGestor)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"membro.saida@teste.com\",\"papel\":\"MEMBRO\"}"))
				.andExpect(status().isCreated());

		// membro sai voluntariamente e perde o acesso
		mockMvc.perform(post("/api/projetos/" + projetoId + "/sair")
				.header("Authorization", "Bearer " + tokenMembro))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/projetos/" + projetoId)
				.header("Authorization", "Bearer " + tokenMembro))
				.andExpect(status().isForbidden());

		// gestor nao pode sair do proprio projeto
		mockMvc.perform(post("/api/projetos/" + projetoId + "/sair")
				.header("Authorization", "Bearer " + tokenGestor))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.erro").value("O Gestor nao pode sair do proprio projeto."));
	}

	@Test
	void projetoEncerradoNaoPodeSerEditado() throws Exception {
		String token = cadastrarELogar("Gestor Encerra", "gestor.encerra@teste.com");
		long projetoId = criarProjeto(token, "Projeto Encerrado");

		mockMvc.perform(put("/api/projetos/" + projetoId + "/encerrar")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ENCERRADO"));

		mockMvc.perform(put("/api/projetos/" + projetoId)
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Novo nome\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erro").value("Um projeto encerrado nao pode ser editado."));
	}

	@Test
	void quemSaiuPodeSerConvidadoDeNovo() throws Exception {
		String tokenGestor = cadastrarELogar("Gestor Volta", "gestor.volta@teste.com");
		String tokenMembro = cadastrarELogar("Membro Volta", "membro.volta@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto Volta");

		String corpoConvite = "{\"email\":\"membro.volta@teste.com\",\"papel\":\"MEMBRO\"}";
		mockMvc.perform(post("/api/projetos/" + projetoId + "/membros")
				.header("Authorization", "Bearer " + tokenGestor)
				.contentType(MediaType.APPLICATION_JSON).content(corpoConvite))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/projetos/" + projetoId + "/sair")
				.header("Authorization", "Bearer " + tokenMembro))
				.andExpect(status().isOk());

		// convite de novo: o vinculo antigo e reativado, desta vez como observador
		mockMvc.perform(post("/api/projetos/" + projetoId + "/membros")
				.header("Authorization", "Bearer " + tokenGestor)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"membro.volta@teste.com\",\"papel\":\"OBSERVADOR\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.papelNoProjeto").value("OBSERVADOR"));

		mockMvc.perform(get("/api/projetos/" + projetoId)
				.header("Authorization", "Bearer " + tokenMembro))
				.andExpect(status().isOk());
	}

}
