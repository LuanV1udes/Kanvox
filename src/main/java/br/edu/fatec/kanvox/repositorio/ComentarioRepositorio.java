package br.edu.fatec.kanvox.repositorio;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.edu.fatec.kanvox.modelo.Comentario;

/** Acesso ao banco para a entidade Comentario (RF-07.1). */
public interface ComentarioRepositorio extends JpaRepository<Comentario, Long> {

	/** Comentarios de uma tarefa, do mais antigo para o mais novo (ordem de conversa). */
	@Query("select c from Comentario c where c.tarefa.id = :tarefaId order by c.criadoEm")
	List<Comentario> buscarPorTarefa(@Param("tarefaId") Long tarefaId);

}
