package br.edu.fatec.kanvox.repositorio;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.edu.fatec.kanvox.modelo.HistoricoTarefa;

/** Acesso ao banco para a entidade HistoricoTarefa. */
public interface HistoricoTarefaRepositorio extends JpaRepository<HistoricoTarefa, Long> {

	/** Historico de uma tarefa, do mais recente para o mais antigo. */
	@Query("select h from HistoricoTarefa h where h.tarefa.id = :tarefaId order by h.criadoEm desc")
	List<HistoricoTarefa> buscarPorTarefa(@Param("tarefaId") Long tarefaId);

}
