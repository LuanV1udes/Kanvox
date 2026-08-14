package br.edu.fatec.kanvox.modelo;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Linha do tempo de uma tarefa: quem fez o que e quando (criacao, edicao,
 * mudanca de coluna). Gerado automaticamente pelo TarefaServico a cada
 * acao — nao existe endpoint para o usuario criar um registro manualmente.
 */
@Entity
@Table(name = "historico_tarefa")
public class HistoricoTarefa {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// JsonIgnore: o historico sempre e consultado dentro de uma tarefa,
	// entao nao ha necessidade de repetir a tarefa inteira no JSON
	@JsonIgnore
	@ManyToOne(optional = false)
	@JoinColumn(name = "tarefa_id")
	private Tarefa tarefa;

	@ManyToOne(optional = false)
	@JoinColumn(name = "autor_id")
	private Usuario autor;

	@Column(nullable = false, length = 500)
	private String descricao;

	@Column(nullable = false)
	private LocalDateTime criadoEm = LocalDateTime.now();

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Tarefa getTarefa() {
		return tarefa;
	}

	public void setTarefa(Tarefa tarefa) {
		this.tarefa = tarefa;
	}

	public Usuario getAutor() {
		return autor;
	}

	public void setAutor(Usuario autor) {
		this.autor = autor;
	}

	public String getDescricao() {
		return descricao;
	}

	public void setDescricao(String descricao) {
		this.descricao = descricao;
	}

	public LocalDateTime getCriadoEm() {
		return criadoEm;
	}

	public void setCriadoEm(LocalDateTime criadoEm) {
		this.criadoEm = criadoEm;
	}

}
