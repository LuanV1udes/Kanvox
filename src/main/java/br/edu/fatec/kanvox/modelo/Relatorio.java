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
 * Relatorio de projeto (RF-04), gerado a partir do estado atual das tarefas.
 * O campo transcricaoAudio e opcional: guarda o texto narrado pelo Gestor
 * e transcrito pelo Whisper (RF-05), depois de revisado (RF-05.5).
 */
@Entity
@Table(name = "relatorio")
public class Relatorio {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// JsonIgnore: os relatorios sempre sao consultados dentro de um projeto,
	// entao nao ha necessidade de repetir o projeto inteiro no JSON
	@JsonIgnore
	@ManyToOne(optional = false)
	@JoinColumn(name = "projeto_id")
	private Projeto projeto;

	@Column(nullable = false, columnDefinition = "text")
	private String conteudo;

	@Column(columnDefinition = "text")
	private String transcricaoAudio;

	@ManyToOne(optional = false)
	@JoinColumn(name = "gerado_por_id")
	private Usuario geradoPor;

	@Column(nullable = false)
	private LocalDateTime geradoEm = LocalDateTime.now();

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Projeto getProjeto() {
		return projeto;
	}

	public void setProjeto(Projeto projeto) {
		this.projeto = projeto;
	}

	public String getConteudo() {
		return conteudo;
	}

	public void setConteudo(String conteudo) {
		this.conteudo = conteudo;
	}

	public String getTranscricaoAudio() {
		return transcricaoAudio;
	}

	public void setTranscricaoAudio(String transcricaoAudio) {
		this.transcricaoAudio = transcricaoAudio;
	}

	public Usuario getGeradoPor() {
		return geradoPor;
	}

	public void setGeradoPor(Usuario geradoPor) {
		this.geradoPor = geradoPor;
	}

	public LocalDateTime getGeradoEm() {
		return geradoEm;
	}

	public void setGeradoEm(LocalDateTime geradoEm) {
		this.geradoEm = geradoEm;
	}

}
