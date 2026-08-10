package br.edu.fatec.kanvox.repositorio;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.edu.fatec.kanvox.modelo.Anexo;

/** Acesso ao banco para a entidade Anexo (RF-07.2). */
public interface AnexoRepositorio extends JpaRepository<Anexo, Long> {

	/** Anexos de uma tarefa, do mais recente para o mais antigo. */
	@Query("select a from Anexo a where a.tarefa.id = :tarefaId order by a.criadoEm desc")
	List<Anexo> buscarPorTarefa(@Param("tarefaId") Long tarefaId);

}
