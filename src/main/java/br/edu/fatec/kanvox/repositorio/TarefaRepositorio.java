package br.edu.fatec.kanvox.repositorio;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.edu.fatec.kanvox.modelo.Tarefa;

/** Acesso ao banco para a entidade Tarefa. */
public interface TarefaRepositorio extends JpaRepository<Tarefa, Long> {

	/** Todas as tarefas de um projeto — usado pelo quadro Kanban (RF-03). */
	@Query("select t from Tarefa t where t.projeto.id = :projetoId order by t.criadoEm")
	List<Tarefa> buscarPorProjeto(@Param("projetoId") Long projetoId);

	/**
	 * Tarefas com prazo vencido que ainda precisam de notificacao (RF-06.2):
	 * nao concluidas, ainda nao notificadas e de projetos ativos.
	 */
	@Query("select t from Tarefa t where t.prazo < :hoje"
			+ " and t.status <> br.edu.fatec.kanvox.modelo.StatusTarefa.CONCLUIDO"
			+ " and t.notificadoAtraso = false"
			+ " and t.projeto.status = br.edu.fatec.kanvox.modelo.StatusProjeto.ATIVO")
	List<Tarefa> buscarAtrasadas(@Param("hoje") LocalDate hoje);

	/**
	 * Tarefas que tem a tarefa informada como dependencia — usado para limpar
	 * a referencia antes de excluir (senao a chave estrangeira impede).
	 */
	@Query("select t from Tarefa t join t.dependencias d where d.id = :tarefaId")
	List<Tarefa> buscarQueDependemDe(@Param("tarefaId") Long tarefaId);

}
