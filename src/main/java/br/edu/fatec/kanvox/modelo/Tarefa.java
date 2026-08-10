package br.edu.fatec.kanvox.modelo;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Tarefa do quadro Kanban (RF-03).
 * Cada tarefa tem um unico responsavel (RF-03.5) e um status
 * que corresponde a coluna do quadro (RF-03.1).
 */
@Entity
@Table(name = "tarefa")
public class Tarefa {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// JsonIgnore: as tarefas sempre sao consultadas dentro de um projeto,
	// entao nao ha necessidade de repetir o projeto inteiro em cada tarefa do JSON
	@JsonIgnore
	@ManyToOne(optional = false)
	@JoinColumn(name = "projeto_id")
	private Projeto projeto;

	@Column(nullable = false)
	private String titulo;

	@Column(length = 2000)
	private String descricao;

	@ManyToOne
	@JoinColumn(name = "responsavel_id")
	private Usuario responsavel;

	private LocalDate prazo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private StatusTarefa status = StatusTarefa.A_FAZER;

	@Column(nullable = false)
	private LocalDateTime criadoEm = LocalDateTime.now();

	// Preenchida quando a tarefa entra na coluna Concluido — usada pelo
	// relatorio para listar o que foi concluido no periodo (RF-04.2)
	private LocalDateTime concluidaEm;

	// Controle interno da rotina de atrasos (RF-06.2): evita notificar o mesmo
	// atraso repetidamente a cada execucao agendada. Zerado quando o prazo muda.
	@JsonIgnore
	@Column(nullable = false, columnDefinition = "boolean default false")
	private boolean notificadoAtraso = false;

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

	public String getTitulo() {
		return titulo;
	}

	public void setTitulo(String titulo) {
		this.titulo = titulo;
	}

	public String getDescricao() {
		return descricao;
	}

	public void setDescricao(String descricao) {
		this.descricao = descricao;
	}

	public Usuario getResponsavel() {
		return responsavel;
	}

	public void setResponsavel(Usuario responsavel) {
		this.responsavel = responsavel;
	}

	public LocalDate getPrazo() {
		return prazo;
	}

	public void setPrazo(LocalDate prazo) {
		this.prazo = prazo;
	}

	public StatusTarefa getStatus() {
		return status;
	}

	public void setStatus(StatusTarefa status) {
		this.status = status;
	}

	public LocalDateTime getCriadoEm() {
		return criadoEm;
	}

	public void setCriadoEm(LocalDateTime criadoEm) {
		this.criadoEm = criadoEm;
	}

	public LocalDateTime getConcluidaEm() {
		return concluidaEm;
	}

	public void setConcluidaEm(LocalDateTime concluidaEm) {
		this.concluidaEm = concluidaEm;
	}

	public boolean isNotificadoAtraso() {
		return notificadoAtraso;
	}

	public void setNotificadoAtraso(boolean notificadoAtraso) {
		this.notificadoAtraso = notificadoAtraso;
	}

}
