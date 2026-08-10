package br.edu.fatec.kanvox.repositorio;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.edu.fatec.kanvox.modelo.Relatorio;

/** Acesso ao banco para a entidade Relatorio. */
public interface RelatorioRepositorio extends JpaRepository<Relatorio, Long> {

	/** Relatorios de um projeto, do mais recente para o mais antigo (RF-04). */
	@Query("select r from Relatorio r where r.projeto.id = :projetoId order by r.geradoEm desc")
	List<Relatorio> buscarPorProjeto(@Param("projetoId") Long projetoId);

}
