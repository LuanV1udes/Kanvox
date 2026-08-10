package br.edu.fatec.kanvox.servico;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.edu.fatec.kanvox.modelo.Notificacao;
import br.edu.fatec.kanvox.modelo.Tarefa;
import br.edu.fatec.kanvox.modelo.TipoNotificacao;
import br.edu.fatec.kanvox.modelo.Usuario;
import br.edu.fatec.kanvox.repositorio.NotificacaoRepositorio;
import br.edu.fatec.kanvox.repositorio.TarefaRepositorio;

/**
 * Regras de negocio das notificacoes internas da plataforma (RF-06).
 * As notificacoes de tarefa bloqueada e tarefa atribuida sao criadas
 * pelo TarefaServico no momento da acao; a de prazo vencido e criada
 * pela rotina agendada deste servico (RF-06.2).
 */
@Service
@Transactional
public class NotificacaoServico {

	private static final DateTimeFormatter FORMATO_DE_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final NotificacaoRepositorio notificacaoRepositorio;
	private final TarefaRepositorio tarefaRepositorio;

	public NotificacaoServico(NotificacaoRepositorio notificacaoRepositorio,
			TarefaRepositorio tarefaRepositorio) {
		this.notificacaoRepositorio = notificacaoRepositorio;
		this.tarefaRepositorio = tarefaRepositorio;
	}

	/** Lista as notificacoes do usuario logado, das mais recentes para as mais antigas (RF-06.4). */
	public List<Notificacao> listarDoUsuario(Usuario usuarioLogado) {
		return notificacaoRepositorio.buscarPorUsuario(usuarioLogado.getId());
	}

	/** Marca uma notificacao como lida — somente o proprio destinatario. */
	public Notificacao marcarComoLida(Usuario usuarioLogado, Long notificacaoId) {
		Notificacao notificacao = notificacaoRepositorio.findById(notificacaoId)
				.orElseThrow(() -> new RegraDeNegocioExcecao("Notificacao nao encontrada."));
		if (!notificacao.getUsuario().getId().equals(usuarioLogado.getId())) {
			throw new PermissaoNegadaExcecao("Esta notificacao nao pertence a voce.");
		}
		notificacao.setLida(true);
		return notificacaoRepositorio.save(notificacao);
	}

	/** Marca todas as notificacoes do usuario logado como lidas. */
	public void marcarTodasComoLidas(Usuario usuarioLogado) {
		List<Notificacao> notificacoes = notificacaoRepositorio.buscarPorUsuario(usuarioLogado.getId());
		for (Notificacao notificacao : notificacoes) {
			notificacao.setLida(true);
		}
		notificacaoRepositorio.saveAll(notificacoes);
	}

	/** Cria uma notificacao — chamado pelos outros servicos (ex. TarefaServico). */
	public void criar(Usuario destinatario, TipoNotificacao tipo, String mensagem) {
		Notificacao notificacao = new Notificacao();
		notificacao.setUsuario(destinatario);
		notificacao.setTipo(tipo);
		notificacao.setMensagem(mensagem);
		notificacaoRepositorio.save(notificacao);
	}

	/**
	 * Rotina agendada de prazos vencidos (RF-06.2): roda a cada 30 minutos,
	 * localiza tarefas atrasadas ainda nao notificadas e avisa o Gestor do
	 * projeto. A tarefa e marcada para nao gerar aviso repetido — se o prazo
	 * for alterado depois, a marcacao e zerada e ela volta a ser avaliada.
	 */
	@Scheduled(initialDelay = 1, fixedDelay = 30, timeUnit = TimeUnit.MINUTES)
	public void notificarTarefasAtrasadas() {
		List<Tarefa> tarefasAtrasadas = tarefaRepositorio.buscarAtrasadas(LocalDate.now());
		for (Tarefa tarefa : tarefasAtrasadas) {
			Usuario gestor = tarefa.getProjeto().getCriadoPor();
			criar(gestor, TipoNotificacao.PRAZO_VENCIDO,
					"A tarefa '" + tarefa.getTitulo() + "' do projeto '" + tarefa.getProjeto().getNome()
							+ "' esta com o prazo vencido desde " + tarefa.getPrazo().format(FORMATO_DE_DATA) + ".");
			tarefa.setNotificadoAtraso(true);
			tarefaRepositorio.save(tarefa);
		}
	}

}
