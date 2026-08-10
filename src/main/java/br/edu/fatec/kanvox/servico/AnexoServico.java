package br.edu.fatec.kanvox.servico;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.edu.fatec.kanvox.modelo.Anexo;
import br.edu.fatec.kanvox.modelo.MembroProjeto;
import br.edu.fatec.kanvox.modelo.PapelProjeto;
import br.edu.fatec.kanvox.modelo.StatusProjeto;
import br.edu.fatec.kanvox.modelo.Tarefa;
import br.edu.fatec.kanvox.modelo.Usuario;
import br.edu.fatec.kanvox.repositorio.AnexoRepositorio;
import br.edu.fatec.kanvox.repositorio.TarefaRepositorio;

/**
 * Regras de negocio dos anexos nas tarefas (RF-07.2) — a devolutiva em
 * arquivo. Quem pode anexar segue a mesma regra de alterar a tarefa:
 * Gestor em qualquer uma, Membro apenas nas atribuidas a ele.
 */
@Service
@Transactional
public class AnexoServico {

	/** Limite por arquivo: 10MB (mantem o custo de operacao proximo de zero, RNF-07). */
	private static final long TAMANHO_MAXIMO = 10L * 1024 * 1024;

	private final AnexoRepositorio anexoRepositorio;
	private final TarefaRepositorio tarefaRepositorio;
	private final TarefaServico tarefaServico;
	private final ProjetoServico projetoServico;

	public AnexoServico(AnexoRepositorio anexoRepositorio,
			TarefaRepositorio tarefaRepositorio,
			TarefaServico tarefaServico,
			ProjetoServico projetoServico) {
		this.anexoRepositorio = anexoRepositorio;
		this.tarefaRepositorio = tarefaRepositorio;
		this.tarefaServico = tarefaServico;
		this.projetoServico = projetoServico;
	}

	/** Lista os anexos de uma tarefa (sem os bytes) — qualquer membro ativo do projeto. */
	public List<Anexo> listar(Usuario usuarioLogado, Long tarefaId) {
		Tarefa tarefa = buscarTarefa(tarefaId);
		projetoServico.buscarVinculoObrigatorio(tarefa.getProjeto().getId(), usuarioLogado);
		return anexoRepositorio.buscarPorTarefa(tarefaId);
	}

	/** Anexa um arquivo a tarefa (Gestor em qualquer uma; Membro so nas dele). */
	public Anexo enviar(Usuario usuarioLogado, Long tarefaId, String nome, String tipo, byte[] dados) {
		Tarefa tarefa = buscarTarefa(tarefaId);
		tarefaServico.validarPodeAlterar(tarefa, usuarioLogado);
		validarProjetoAtivo(tarefa);

		if (dados == null || dados.length == 0) {
			throw new RegraDeNegocioExcecao("Nenhum arquivo foi enviado.");
		}
		if (dados.length > TAMANHO_MAXIMO) {
			throw new RegraDeNegocioExcecao("O arquivo excede o limite de 10MB.");
		}

		Anexo anexo = new Anexo();
		anexo.setTarefa(tarefa);
		anexo.setNome(nome == null || nome.isBlank() ? "arquivo" : nome);
		anexo.setTipo(tipo == null || tipo.isBlank() ? "application/octet-stream" : tipo);
		anexo.setTamanho(dados.length);
		anexo.setDados(dados);
		anexo.setEnviadoPor(usuarioLogado);
		return anexoRepositorio.save(anexo);
	}

	/** Devolve o anexo com os bytes, para download — qualquer membro ativo do projeto. */
	public Anexo baixar(Usuario usuarioLogado, Long anexoId) {
		Anexo anexo = buscarAnexo(anexoId);
		projetoServico.buscarVinculoObrigatorio(anexo.getTarefa().getProjeto().getId(), usuarioLogado);
		return anexo;
	}

	/** Exclui um anexo — somente quem enviou ou o Gestor do projeto. */
	public void excluir(Usuario usuarioLogado, Long anexoId) {
		Anexo anexo = buscarAnexo(anexoId);
		MembroProjeto vinculo = projetoServico.buscarVinculoObrigatorio(
				anexo.getTarefa().getProjeto().getId(), usuarioLogado);

		boolean enviei = anexo.getEnviadoPor().getId().equals(usuarioLogado.getId());
		boolean souOGestor = vinculo.getPapelNoProjeto() == PapelProjeto.GESTOR;
		if (!enviei && !souOGestor) {
			throw new PermissaoNegadaExcecao("Somente quem enviou o anexo ou o Gestor podem exclui-lo.");
		}
		validarProjetoAtivo(anexo.getTarefa());
		anexoRepositorio.delete(anexo);
	}

	private Tarefa buscarTarefa(Long tarefaId) {
		return tarefaRepositorio.findById(tarefaId)
				.orElseThrow(() -> new RegraDeNegocioExcecao("Tarefa nao encontrada."));
	}

	private Anexo buscarAnexo(Long anexoId) {
		return anexoRepositorio.findById(anexoId)
				.orElseThrow(() -> new RegraDeNegocioExcecao("Anexo nao encontrado."));
	}

	private void validarProjetoAtivo(Tarefa tarefa) {
		if (tarefa.getProjeto().getStatus() == StatusProjeto.ENCERRADO) {
			throw new RegraDeNegocioExcecao("O projeto esta encerrado: os anexos nao podem mais ser alterados.");
		}
	}

}
