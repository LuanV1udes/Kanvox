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
 * Comentario dentro de uma tarefa (RF-07.1) — o canal de devolutiva
 * entre Membro e Gestor ("terminei, segue o resultado" / "refaz a parte X").
 */
@Entity
@Table(name = "comentario")
public class Comentario {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// JsonIgnore: os comentarios sempre sao consultados dentro de uma tarefa
	@JsonIgnore
	@ManyToOne(optional = false)
	@JoinColumn(name = "tarefa_id")
	private Tarefa tarefa;

	@ManyToOne(optional = false)
	@JoinColumn(name = "autor_id")
	private Usuario autor;

	@Column(nullable = false, length = 2000)
	private String texto;

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

	public String getTexto() {
		return texto;
	}

	public void setTexto(String texto) {
		this.texto = texto;
	}

	public LocalDateTime getCriadoEm() {
		return criadoEm;
	}

	public void setCriadoEm(LocalDateTime criadoEm) {
		this.criadoEm = criadoEm;
	}

}
