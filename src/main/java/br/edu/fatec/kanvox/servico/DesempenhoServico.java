package br.edu.fatec.kanvox.servico;

import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.edu.fatec.kanvox.modelo.MembroProjeto;
import br.edu.fatec.kanvox.modelo.PapelProjeto;
import br.edu.fatec.kanvox.modelo.StatusTarefa;
import br.edu.fatec.kanvox.modelo.Tarefa;
import br.edu.fatec.kanvox.modelo.Usuario;
import br.edu.fatec.kanvox.repositorio.MembroProjetoRepositorio;
import br.edu.fatec.kanvox.repositorio.TarefaRepositorio;

/**
 * Painel de desempenho do projeto — visao de carga de trabalho e eficiencia
 * de entrega da equipe, exclusiva do Gestor. Nao e um ranking individual:
 * a ideia e mostrar onde estao os gargalos e como esta a distribuicao do
 * trabalho, nao comparar pessoas.
 *
 * Tudo e calculado em cima dos dados que ja existem em Tarefa (responsaveis,
 * status, prazo, criadoEm, concluidaEm) — nenhuma tabela nova.
 */
@Service
@Transactional
public class DesempenhoServico {

	private final TarefaRepositorio tarefaRepositorio;
	private final MembroProjetoRepositorio membroProjetoRepositorio;
	private final ProjetoServico projetoServico;

	public DesempenhoServico(TarefaRepositorio tarefaRepositorio,
			MembroProjetoRepositorio membroProjetoRepositorio,
			ProjetoServico projetoServico) {
		this.tarefaRepositorio = tarefaRepositorio;
		this.membroProjetoRepositorio = membroProjetoRepositorio;
		this.projetoServico = projetoServico;
	}

	/** Gera o painel de desempenho do projeto — somente o Gestor pode consultar. */
	public Map<String, Object> gerar(Usuario usuarioLogado, Long projetoId) {
		projetoServico.validarGestor(projetoId, usuarioLogado);
		List<Tarefa> tarefas = tarefaRepositorio.buscarPorProjeto(projetoId);

		// Observador nunca pode ser responsavel por tarefa (RF-03.5): fica de fora da tabela
		List<Usuario> membros = membroProjetoRepositorio.buscarMembrosAtivos(projetoId).stream()
				.filter(membro -> membro.getPapelNoProjeto() != PapelProjeto.OBSERVADOR)
				.map(MembroProjeto::getUsuario)
				.toList();

		List<Map<String, Object>> porMembro = new ArrayList<>();
		for (Usuario membro : membros) {
			porMembro.add(calcularDesempenhoDoMembro(membro, tarefas));
		}

		Map<String, Object> resultado = new HashMap<>();
		resultado.put("resumo", calcularResumoDoTime(tarefas));
		resultado.put("porMembro", porMembro);
		return resultado;
	}

	private Map<String, Object> calcularResumoDoTime(List<Tarefa> tarefas) {
		long totalDeTarefas = tarefas.size();
		long concluidas = tarefas.stream().filter(t -> t.getStatus() == StatusTarefa.CONCLUIDO).count();
		int progresso = totalDeTarefas == 0 ? 0 : (int) (concluidas * 100 / totalDeTarefas);

		long bloqueadas = tarefas.stream().filter(t -> t.getStatus() == StatusTarefa.BLOQUEADO).count();

		List<Tarefa> concluidasComPrazo = tarefas.stream()
				.filter(t -> t.getStatus() == StatusTarefa.CONCLUIDO
						&& t.getPrazo() != null && t.getConcluidaEm() != null)
				.toList();
		Integer percentualNoPrazo = null;
		if (!concluidasComPrazo.isEmpty()) {
			long noPrazo = concluidasComPrazo.stream()
					.filter(t -> !t.getConcluidaEm().toLocalDate().isAfter(t.getPrazo()))
					.count();
			percentualNoPrazo = (int) (noPrazo * 100 / concluidasComPrazo.size());
		}

		Map<String, Object> resumo = new HashMap<>();
		resumo.put("progresso", progresso);
		resumo.put("tarefasBloqueadas", bloqueadas);
		resumo.put("percentualNoPrazo", percentualNoPrazo);
		return resumo;
	}

	private Map<String, Object> calcularDesempenhoDoMembro(Usuario membro, List<Tarefa> todasAsTarefas) {
		List<Tarefa> doMembro = todasAsTarefas.stream()
				.filter(tarefa -> tarefa.getResponsaveis().stream()
						.anyMatch(responsavel -> responsavel.getId().equals(membro.getId())))
				.toList();

		long concluidas = doMembro.stream().filter(t -> t.getStatus() == StatusTarefa.CONCLUIDO).count();
		long emAberto = doMembro.size() - concluidas;
		long atrasadas = doMembro.stream()
				.filter(t -> t.getStatus() != StatusTarefa.CONCLUIDO
						&& t.getPrazo() != null && t.getPrazo().isBefore(LocalDate.now()))
				.count();

		List<Tarefa> concluidasComData = doMembro.stream()
				.filter(t -> t.getStatus() == StatusTarefa.CONCLUIDO && t.getConcluidaEm() != null)
				.toList();
		Double tempoMedioDeConclusaoEmDias = concluidasComData.isEmpty() ? null
				: concluidasComData.stream()
						.mapToDouble(t -> Duration.between(t.getCriadoEm(), t.getConcluidaEm()).toHours() / 24.0)
						.average()
						.orElse(0);

		// eficiencia = prazo - data de conclusao, em dias: positivo e entrega adiantada,
		// negativo e atraso. So entram tarefas concluidas que tinham prazo definido.
		List<Tarefa> concluidasComPrazo = concluidasComData.stream()
				.filter(t -> t.getPrazo() != null)
				.toList();
		Double eficienciaMediaEmDias = concluidasComPrazo.isEmpty() ? null
				: concluidasComPrazo.stream()
						.mapToLong(t -> ChronoUnit.DAYS.between(t.getConcluidaEm().toLocalDate(), t.getPrazo()))
						.average()
						.orElse(0);

		Map<String, Object> dados = new HashMap<>();
		dados.put("usuario", membro);
		dados.put("concluidas", concluidas);
		dados.put("emAberto", emAberto);
		dados.put("atrasadas", atrasadas);
		dados.put("tempoMedioDeConclusaoEmDias", tempoMedioDeConclusaoEmDias);
		dados.put("eficienciaMediaEmDias", eficienciaMediaEmDias);
		return dados;
	}

}
