/* ============================================================
   projeto.js — pagina do projeto: quadro Kanban (RF-03),
   membros (RF-01), relatorios (RF-04) e narracao por voz (RF-05).
   O quadro e as notificacoes sao atualizados por polling a cada
   5 segundos (RF-03.6 / RNF-01), sem WebSocket — decisao de projeto.
   ============================================================ */

exigirSessao();

// As secoes da pagina viram itens da barra lateral: a primeira
// (o quadro) e a que aparece ao abrir o projeto. "Desempenho" e exclusiva
// do Gestor — comeca escondida e ajustarPermissoesDaTela() a libera.
montarNavegacao([
	{ visao: 'quadro', rotulo: 'Quadro', icone: 'quadro' },
	{ visao: 'membros', rotulo: 'Membros', icone: 'membros' },
	{ visao: 'linha-do-tempo', rotulo: 'Linha do tempo', icone: 'linhadotempo' },
	{ visao: 'relatorios', rotulo: 'Relatórios', icone: 'relatorios' },
	{ visao: 'desempenho', rotulo: 'Desempenho', icone: 'desempenho', somenteGestor: true }
], (visao) => {
	// ao voltar para os relatorios, busca a lista de novo (pode ter mudado)
	if (visao === 'relatorios') {
		carregarRelatorios().catch(erro => exibirMensagem(erro.message, 'erro'));
	}
	if (visao === 'desempenho') {
		carregarDesempenho().catch(erro => exibirMensagem(erro.message, 'erro'));
	}
});

const projetoId = new URLSearchParams(window.location.search).get('id');
const usuario = usuarioLogado();

const COLUNAS = [
	{ status: 'A_FAZER', titulo: 'A Fazer' },
	{ status: 'EM_ANDAMENTO', titulo: 'Em Andamento' },
	{ status: 'BLOQUEADO', titulo: 'Bloqueado' },
	{ status: 'EM_REVISAO', titulo: 'Em Revisão' },
	{ status: 'CONCLUIDO', titulo: 'Concluído' }
];

/* Icones usados junto com texto (botao de gravar, badge de dependencia...),
   desenhados em SVG — sem emoji, mesmo espirito dos icones de navegacao.js. */
const ICONE_MICROFONE = '<svg class="icone-inline" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="9" y="2" width="6" height="11" rx="3"></rect><path d="M5 10v1a7 7 0 0 0 14 0v-1"></path><path d="M12 18v4M8 22h8"></path></svg>';
const ICONE_PARAR = '<svg class="icone-inline" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="6" y="6" width="12" height="12" rx="2"></rect></svg>';
const ICONE_AGUARDANDO = '<svg class="icone-inline" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="9"></circle><path d="M12 7v5l3 3"></path></svg>';

const ROTULOS_DE_PRIORIDADE = { BAIXA: 'Baixa', MEDIA: 'Média', ALTA: 'Alta' };
const ROTULOS_DE_STATUS = Object.fromEntries(COLUNAS.map(coluna => [coluna.status, coluna.titulo]));
// reaproveita as cores ja usadas nos selos de papel/status do projeto — sem CSS novo
const SELOS_DE_STATUS = {
	A_FAZER: 'selo-encerrado',
	EM_ANDAMENTO: 'selo-gestor',
	BLOQUEADO: 'selo-perigo',
	EM_REVISAO: 'selo-aviso',
	CONCLUIDO: 'selo-ativo'
};

let projeto = null;
let membros = [];
let tarefas = null;    // null (nao []) forca o primeiro carregarTarefas() a sempre renderizar,
                        // mesmo quando o projeto ainda nao tem nenhuma tarefa (ver carregarTarefas)
let meuPapel = null;
let arrastando = false;          // pausa o polling enquanto um cartao e arrastado
let tarefaEmEdicao = null;       // tarefa aberta no dialogo (null = criando nova)
let audioGravado = null;         // Blob com a narracao gravada (RF-05.1)
let gravador = null;             // MediaRecorder

/* ---------- carga inicial e polling ---------- */

async function carregarVisaoGeral() {
	const visao = await chamarApi('/projetos/' + projetoId);
	projeto = visao.projeto;
	membros = visao.membros;
	const meuVinculo = membros.find(membro => membro.usuario.id === usuario.id);
	meuPapel = meuVinculo ? meuVinculo.papelNoProjeto : null;

	document.title = 'Kanvox — ' + projeto.nome;
	document.getElementById('nome-do-projeto').textContent = projeto.nome;
	document.getElementById('descricao-do-projeto').textContent = projeto.descricao || '';

	const seloStatus = document.getElementById('selo-status');
	seloStatus.textContent = projeto.status === 'ATIVO' ? 'Ativo' : 'Encerrado';
	seloStatus.className = 'selo ' + (projeto.status === 'ATIVO' ? 'selo-ativo' : 'selo-encerrado');

	const rotulosDePapel = { GESTOR: 'Gestor', MEMBRO: 'Membro', OBSERVADOR: 'Observador' };
	const seloPapel = document.getElementById('selo-meu-papel');
	seloPapel.textContent = 'Você: ' + rotulosDePapel[meuPapel];
	seloPapel.className = 'selo selo-' + meuPapel.toLowerCase();

	document.getElementById('barra-de-progresso').style.width = visao.progresso + '%';
	document.getElementById('resumo-do-progresso').textContent =
		visao.progresso + '% concluído — ' + visao.tarefasConcluidas + ' de ' + visao.totalTarefas + ' tarefas';

	const indicadorBloqueadas = document.getElementById('indicador-bloqueadas');
	indicadorBloqueadas.hidden = visao.tarefasBloqueadas === 0;
	indicadorBloqueadas.textContent = visao.tarefasBloqueadas + (visao.tarefasBloqueadas === 1 ? ' bloqueada' : ' bloqueadas');

	const indicadorAtrasadas = document.getElementById('indicador-atrasadas');
	indicadorAtrasadas.hidden = visao.tarefasAtrasadas === 0;
	indicadorAtrasadas.textContent = visao.tarefasAtrasadas + (visao.tarefasAtrasadas === 1 ? ' tarefa atrasada' : ' tarefas atrasadas');

	ajustarPermissoesDaTela();
	renderizarMembros();
	preencherFiltroDeResponsaveis();
}

function podeEscrever() {
	return projeto.status === 'ATIVO' && meuPapel !== 'OBSERVADOR';
}

function souGestor() {
	return meuPapel === 'GESTOR';
}

/** Mostra/esconde os botoes conforme o papel do usuario (a validacao real e no backend). */
function ajustarPermissoesDaTela() {
	// criar tarefas e exclusivo do Gestor (RF-03.2)
	document.getElementById('botao-nova-tarefa').hidden = !(souGestor() && projeto.status === 'ATIVO');
	document.getElementById('botao-editar-projeto').hidden = !(souGestor() && projeto.status === 'ATIVO');
	document.getElementById('botao-encerrar-projeto').hidden = !(souGestor() && projeto.status === 'ATIVO');
	document.getElementById('formulario-convite').hidden = !(souGestor() && projeto.status === 'ATIVO');
	document.getElementById('botao-sair-do-projeto').hidden = souGestor();
	document.getElementById('botao-novo-relatorio').hidden = !souGestor();
	document.querySelector('.item-de-navegacao[data-visao="desempenho"]').hidden = !souGestor();
}

async function carregarTarefas() {
	const novas = await chamarApi('/projetos/' + projetoId + '/tarefas');
	// so redesenha se algo mudou (evita piscadas no polling)
	if (JSON.stringify(novas) !== JSON.stringify(tarefas)) {
		tarefas = novas;
		renderizarQuadro();
		renderizarLinhaDoTempo();
	}
}

setInterval(() => {
	const algumDialogoAberto = document.querySelector('dialog[open]');
	if (!arrastando && !algumDialogoAberto) {
		carregarTarefas().catch(() => {});
	}
}, 5000);

/* ---------- filtro do quadro: busca por titulo, responsavel e prioridade ---------- */
/* Filtro puramente visual, no frontend: o quadro ja recebe todas as tarefas
   do projeto a cada polling, entao filtrar aqui nao exige nenhuma rota nova. */

function preencherFiltroDeResponsaveis() {
	const select = document.getElementById('filtro-responsavel');
	const selecionado = select.value;
	const opcoes = membros
		.filter(membro => membro.papelNoProjeto !== 'OBSERVADOR' && membro.situacao === 'ATIVO')
		.map(membro => `<option value="${membro.usuario.id}">${escaparHtml(membro.usuario.nome)}</option>`);
	select.innerHTML = '<option value="">Todos os responsáveis</option>' + opcoes.join('');
	select.value = selecionado;
}

function aplicarFiltros(lista) {
	const texto = document.getElementById('filtro-busca').value.trim().toLowerCase();
	const responsavelId = document.getElementById('filtro-responsavel').value;
	const prioridade = document.getElementById('filtro-prioridade').value;
	return lista.filter(tarefa => {
		if (texto && !tarefa.titulo.toLowerCase().includes(texto)) {
			return false;
		}
		if (responsavelId && !tarefa.responsaveis.some(responsavel => String(responsavel.id) === responsavelId)) {
			return false;
		}
		if (prioridade && tarefa.prioridade !== prioridade) {
			return false;
		}
		return true;
	});
}

document.getElementById('filtro-busca').addEventListener('input', renderizarQuadro);
document.getElementById('filtro-responsavel').addEventListener('change', renderizarQuadro);
document.getElementById('filtro-prioridade').addEventListener('change', renderizarQuadro);
document.getElementById('botao-limpar-filtros').addEventListener('click', () => {
	document.getElementById('filtro-busca').value = '';
	document.getElementById('filtro-responsavel').value = '';
	document.getElementById('filtro-prioridade').value = '';
	renderizarQuadro();
});

/* ---------- quadro kanban (RF-03) ---------- */

function renderizarQuadro() {
	const quadro = document.getElementById('quadro');
	const tarefasFiltradas = aplicarFiltros(tarefas);
	quadro.innerHTML = COLUNAS.map(coluna => {
		const tarefasDaColuna = tarefasFiltradas.filter(tarefa => tarefa.status === coluna.status);
		// a <ul> e escrita sem quebras de linha para que uma coluna sem tarefas
		// fique de fato vazia e o CSS possa desenhar a area tracejada (:empty)
		return `
			<div class="coluna" data-status="${coluna.status}">
				<h3>${coluna.titulo} <span>${tarefasDaColuna.length}</span></h3>
				<ul class="lista-de-tarefas" data-status="${coluna.status}">${tarefasDaColuna.map(montarCartaoDeTarefa).join('')}</ul>
			</div>
		`;
	}).join('');

	// clique no cartao abre o dialogo de edicao/visualizacao
	quadro.querySelectorAll('.cartao-tarefa').forEach(cartao => {
		cartao.addEventListener('click', () => {
			const tarefa = tarefas.find(t => t.id === Number(cartao.dataset.id));
			abrirDialogoDeTarefa(tarefa);
		});
	});

	// drag-and-drop entre colunas (RF-03.4), desativado para quem so le
	if (podeEscrever()) {
		quadro.querySelectorAll('.lista-de-tarefas').forEach(lista => {
			new Sortable(lista, {
				group: 'tarefas',
				animation: 150,
				filter: '.nao-arrastavel', // membro nao arrasta tarefa dos outros
				onStart: () => { arrastando = true; },
				// concluir (ou reabrir) uma tarefa e decisao exclusiva do Gestor (RF-03.7)
				onMove: (evento) => souGestor() || evento.to.dataset.status !== 'CONCLUIDO',
				onEnd: aoSoltarCartao
			});
		});
	}
}

/** Resume os nomes dos responsaveis em texto curto (o cartao tem pouco espaco). */
function nomesDosResponsaveis(tarefa) {
	if (!tarefa.responsaveis || tarefa.responsaveis.length === 0) {
		return 'Sem responsável';
	}
	const nomes = tarefa.responsaveis.map(responsavel => responsavel.nome);
	return nomes.length <= 2 ? nomes.join(', ') : nomes.slice(0, 2).join(', ') + ' +' + (nomes.length - 2);
}

/** Dependencias desta tarefa que ainda nao foram concluidas — ela nao sai de "A Fazer" enquanto existirem. */
function dependenciasPendentes(tarefa) {
	return (tarefa.dependencias || []).filter(dependencia => dependencia.status !== 'CONCLUIDO');
}

function montarCartaoDeTarefa(tarefa) {
	// membro so pode mover tarefas em que e um dos responsaveis, e nao pode
	// tirar uma tarefa de Concluido — isso e decisao exclusiva do Gestor (RF-03.3 / RF-03.7)
	const souUmDosResponsaveis = tarefa.responsaveis.some(responsavel => responsavel.id === usuario.id);
	const arrastavel = souGestor() || (souUmDosResponsaveis && tarefa.status !== 'CONCLUIDO');
	const prazoVencido = tarefa.prazo && tarefa.status !== 'CONCLUIDO'
		&& tarefa.prazo < new Date().toISOString().slice(0, 10);
	const pendentes = dependenciasPendentes(tarefa);
	return `
		<li class="cartao-tarefa ${arrastavel ? '' : 'nao-arrastavel'}" data-id="${tarefa.id}" data-prioridade="${tarefa.prioridade}">
			<div class="titulo-da-tarefa">${escaparHtml(tarefa.titulo)}</div>
			${pendentes.length > 0 ? `<div class="aviso-dependencia" title="Aguardando: ${escaparHtml(pendentes.map(d => d.titulo).join(', '))}">${ICONE_AGUARDANDO}aguarda ${pendentes.length} ${pendentes.length === 1 ? 'tarefa' : 'tarefas'}</div>` : ''}
			<div class="detalhes-da-tarefa">
				<span title="${escaparHtml(tarefa.responsaveis.map(r => r.nome).join(', '))}">${escaparHtml(nomesDosResponsaveis(tarefa))}</span>
				<span class="${prazoVencido ? 'prazo-vencido' : ''}">
					${tarefa.prazo ? formatarData(tarefa.prazo) : ''}
				</span>
			</div>
		</li>
	`;
}

async function aoSoltarCartao(evento) {
	arrastando = false;
	const novoStatus = evento.to.dataset.status;
	const statusAnterior = evento.from.dataset.status;
	if (novoStatus === statusAnterior) {
		return;
	}
	try {
		await chamarApi('/tarefas/' + evento.item.dataset.id + '/status', {
			method: 'PUT',
			body: { status: novoStatus }
		});
	} catch (erro) {
		exibirMensagem(erro.message, 'erro');
	} finally {
		// recarrega o quadro do servidor para garantir que a tela reflete o banco
		// (null, nao [], senao mover a ultima tarefa de volta a coluna original nao re-renderiza)
		tarefas = null;
		carregarTarefas().catch(() => {});
		carregarVisaoGeral().catch(() => {});
	}
}

/* ---------- linha do tempo: tarefas em aberto organizadas por prazo ---------- */
/* Assim como o filtro do quadro, e so uma outra forma de olhar as mesmas
   tarefas que ja chegam pelo polling — nenhuma rota nova. */

const GRUPOS_DA_LINHA_DO_TEMPO = [
	{ chave: 'atrasadas', rotulo: 'Atrasadas' },
	{ chave: 'hoje', rotulo: 'Hoje' },
	{ chave: 'estaSemana', rotulo: 'Esta semana' },
	{ chave: 'maisAdiante', rotulo: 'Mais adiante' },
	{ chave: 'semPrazo', rotulo: 'Sem prazo definido' }
];

/** Separa as tarefas em aberto em baldes de tempo, cada um ja ordenado por prazo. */
function agruparParaLinhaDoTempo(lista) {
	const hoje = new Date().toISOString().slice(0, 10);
	const emUmaSemana = new Date(Date.now() + 7 * 86400000).toISOString().slice(0, 10);
	const grupos = { atrasadas: [], hoje: [], estaSemana: [], maisAdiante: [], semPrazo: [] };

	lista.filter(tarefa => tarefa.status !== 'CONCLUIDO').forEach(tarefa => {
		if (!tarefa.prazo) {
			grupos.semPrazo.push(tarefa);
		} else if (tarefa.prazo < hoje) {
			grupos.atrasadas.push(tarefa);
		} else if (tarefa.prazo === hoje) {
			grupos.hoje.push(tarefa);
		} else if (tarefa.prazo <= emUmaSemana) {
			grupos.estaSemana.push(tarefa);
		} else {
			grupos.maisAdiante.push(tarefa);
		}
	});
	Object.values(grupos).forEach(grupo => grupo.sort((a, b) => (a.prazo || '9999-99-99').localeCompare(b.prazo || '9999-99-99')));
	return grupos;
}

function montarItemDaLinhaDoTempo(tarefa) {
	const pendentes = dependenciasPendentes(tarefa);
	const prazoVencido = tarefa.prazo && tarefa.prazo < new Date().toISOString().slice(0, 10);
	return `
		<li class="item-da-linha-do-tempo" data-id="${tarefa.id}">
			<span class="selo ${SELOS_DE_STATUS[tarefa.status]}">${ROTULOS_DE_STATUS[tarefa.status]}</span>
			<span class="titulo-da-tarefa">${escaparHtml(tarefa.titulo)}</span>
			<span class="texto-suave">${escaparHtml(nomesDosResponsaveis(tarefa))}</span>
			<div class="espacador"></div>
			${pendentes.length > 0 ? `<span class="selo selo-aviso" title="Aguardando: ${escaparHtml(pendentes.map(d => d.titulo).join(', '))}">${ICONE_AGUARDANDO}aguarda ${pendentes.length}</span>` : ''}
			<span class="${prazoVencido ? 'prazo-vencido' : 'texto-suave'}">${tarefa.prazo ? formatarData(tarefa.prazo) : 'Sem prazo'}</span>
		</li>
	`;
}

function renderizarLinhaDoTempo() {
	const contentor = document.getElementById('linha-do-tempo');
	if (!tarefas) {
		return;
	}
	const grupos = agruparParaLinhaDoTempo(tarefas);
	const gruposComTarefas = GRUPOS_DA_LINHA_DO_TEMPO.filter(g => grupos[g.chave].length > 0);

	if (gruposComTarefas.length === 0) {
		contentor.innerHTML = '<p class="texto-suave">Nenhuma tarefa em aberto — tudo concluído por aqui.</p>';
		return;
	}

	contentor.innerHTML = gruposComTarefas.map(g => `
		<div class="grupo-da-linha-do-tempo">
			<h3 class="titulo-do-grupo-da-linha ${g.chave === 'atrasadas' ? 'titulo-atrasado' : ''}">${g.rotulo} <span>${grupos[g.chave].length}</span></h3>
			<ul class="lista-da-linha-do-tempo">${grupos[g.chave].map(montarItemDaLinhaDoTempo).join('')}</ul>
		</div>
	`).join('');

	contentor.querySelectorAll('.item-da-linha-do-tempo').forEach(item => {
		item.addEventListener('click', () => {
			const tarefa = tarefas.find(t => t.id === Number(item.dataset.id));
			abrirDialogoDeTarefa(tarefa);
		});
	});
}

/* ---------- dialogo de tarefa ---------- */

const dialogoTarefa = document.getElementById('dialogo-tarefa');

/** Lista de checkboxes com os membros que podem ser responsaveis pela tarefa (RF-03.5). */
function preencherListaDeResponsaveis(idsSelecionados, desabilitado) {
	const lista = document.getElementById('lista-de-responsaveis');
	// convites pendentes (RF-01.4) nao podem ser responsaveis: so entram no quadro apos aceitar
	const opcoes = membros.filter(membro => membro.papelNoProjeto !== 'OBSERVADOR' && membro.situacao === 'ATIVO');
	if (opcoes.length === 0) {
		lista.innerHTML = '<p class="aviso-vazio-secao">Nenhum membro disponível ainda.</p>';
		return;
	}
	lista.innerHTML = opcoes.map(membro => `
		<label class="item-selecionavel">
			<input type="checkbox" value="${membro.usuario.id}"
				${idsSelecionados.includes(membro.usuario.id) ? 'checked' : ''}
				${desabilitado ? 'disabled' : ''}>
			${escaparHtml(membro.usuario.nome)}
		</label>
	`).join('');
}

/** Le os ids marcados na lista de checkboxes de responsaveis. */
function responsaveisSelecionados() {
	return Array.from(document.querySelectorAll('#lista-de-responsaveis input:checked'))
		.map(caixa => ({ id: Number(caixa.value) }));
}

/** Lista de checkboxes com as outras tarefas do projeto, para marcar como dependencia. */
function preencherListaDeDependencias(tarefaIdAtual, idsSelecionados, desabilitado) {
	const lista = document.getElementById('lista-de-dependencias');
	const opcoes = tarefas.filter(t => t.id !== tarefaIdAtual);
	if (opcoes.length === 0) {
		lista.innerHTML = '<p class="aviso-vazio-secao">Nenhuma outra tarefa no projeto ainda.</p>';
		return;
	}
	lista.innerHTML = opcoes.map(t => `
		<label class="item-selecionavel">
			<input type="checkbox" value="${t.id}"
				${idsSelecionados.includes(t.id) ? 'checked' : ''}
				${desabilitado ? 'disabled' : ''}>
			${escaparHtml(t.titulo)} <span class="texto-suave">(${ROTULOS_DE_STATUS[t.status]})</span>
		</label>
	`).join('');
}

/** Le os ids marcados na lista de checkboxes de dependencias. */
function dependenciasSelecionadas() {
	return Array.from(document.querySelectorAll('#lista-de-dependencias input:checked'))
		.map(caixa => ({ id: Number(caixa.value) }));
}

function abrirDialogoDeTarefa(tarefa) {
	tarefaEmEdicao = tarefa || null;
	const criando = !tarefa;
	document.getElementById('titulo-do-dialogo-tarefa').textContent = criando ? 'Nova tarefa' : 'Tarefa';
	document.getElementById('tarefa-titulo').value = criando ? '' : tarefa.titulo;
	document.getElementById('tarefa-descricao').value = criando ? '' : (tarefa.descricao || '');
	document.getElementById('tarefa-prazo').value = criando ? '' : (tarefa.prazo || '');
	document.getElementById('tarefa-prioridade').value = criando ? 'MEDIA' : tarefa.prioridade;

	// a lista de responsaveis e de dependencias so aparece para o Gestor (unico papel que cria/edita tarefas)
	document.getElementById('rotulo-responsaveis').hidden = !souGestor();
	document.getElementById('rotulo-dependencias').hidden = !souGestor();

	// editar o CONTEUDO da tarefa (titulo/descricao/prazo/prioridade/responsaveis/dependencias)
	// e exclusivo do Gestor (RF-03.2/RF-03.3) — o Membro so movimenta a propria
	// tarefa no quadro (drag-and-drop) e colabora via comentarios/anexos (RF-07)
	const podeEditarConteudo = souGestor() && projeto.status === 'ATIVO';
	if (souGestor()) {
		const idsAtuais = tarefa && tarefa.responsaveis ? tarefa.responsaveis.map(r => r.id) : [];
		preencherListaDeResponsaveis(idsAtuais, !podeEditarConteudo);
		const idsDasDependencias = tarefa && tarefa.dependencias ? tarefa.dependencias.map(d => d.id) : [];
		preencherListaDeDependencias(criando ? null : tarefa.id, idsDasDependencias, !podeEditarConteudo);
	}
	['tarefa-titulo', 'tarefa-descricao', 'tarefa-prazo', 'tarefa-prioridade'].forEach(id => {
		document.getElementById(id).disabled = !podeEditarConteudo;
	});
	document.getElementById('botao-salvar-tarefa').hidden = !podeEditarConteudo;
	document.getElementById('botao-excluir-tarefa').hidden = !(souGestor() && !criando && projeto.status === 'ATIVO');

	// devolutiva (RF-07): comentarios, anexos e historico so existem em tarefas ja criadas.
	// Anexar segue a mesma regra de mover a tarefa (Gestor qualquer, Membro so a propria);
	// comentar e liberado para Gestor e Membro em qualquer tarefa do projeto.
	document.getElementById('secao-colaboracao').hidden = criando;
	if (!criando) {
		const souUmDosResponsaveis = tarefa.responsaveis.some(responsavel => responsavel.id === usuario.id);
		const podeAnexar = podeEscrever() && (souGestor() || souUmDosResponsaveis);
		carregarComentarios(tarefa.id);
		carregarAnexos(tarefa.id);
		carregarHistorico(tarefa.id);
		document.getElementById('linha-de-anexo').hidden = !podeAnexar;
		document.getElementById('linha-de-comentario').hidden = !podeEscrever();
	}

	dialogoTarefa.showModal();
}

document.getElementById('botao-nova-tarefa').addEventListener('click', () => abrirDialogoDeTarefa(null));
document.getElementById('botao-cancelar-tarefa').addEventListener('click', () => dialogoTarefa.close());

document.getElementById('formulario-tarefa').addEventListener('submit', async (evento) => {
	evento.preventDefault();
	const corpo = {
		titulo: document.getElementById('tarefa-titulo').value,
		descricao: document.getElementById('tarefa-descricao').value,
		prazo: document.getElementById('tarefa-prazo').value || null,
		prioridade: document.getElementById('tarefa-prioridade').value
	};
	if (souGestor()) {
		corpo.responsaveis = responsaveisSelecionados();
		corpo.dependencias = dependenciasSelecionadas();
	}
	try {
		if (tarefaEmEdicao) {
			await chamarApi('/tarefas/' + tarefaEmEdicao.id, { method: 'PUT', body: corpo });
		} else {
			await chamarApi('/projetos/' + projetoId + '/tarefas', { method: 'POST', body: corpo });
		}
		dialogoTarefa.close();
		tarefas = null;
		await carregarTarefas();
		await carregarVisaoGeral();
	} catch (erro) {
		exibirMensagem(erro.message, 'erro');
	}
});

document.getElementById('botao-excluir-tarefa').addEventListener('click', async () => {
	if (!confirm('Excluir esta tarefa? Essa ação não pode ser desfeita.')) {
		return;
	}
	try {
		await chamarApi('/tarefas/' + tarefaEmEdicao.id, { method: 'DELETE' });
		dialogoTarefa.close();
		// null (nao []): exclui a ultima tarefa de uma coluna tambem precisa re-renderizar
		tarefas = null;
		await carregarTarefas();
		await carregarVisaoGeral();
	} catch (erro) {
		exibirMensagem(erro.message, 'erro');
	}
});

/* ---------- comentarios e anexos na tarefa: a devolutiva (RF-07) ---------- */

async function carregarComentarios(tarefaId) {
	try {
		const comentarios = await chamarApi('/tarefas/' + tarefaId + '/comentarios');
		const lista = document.getElementById('lista-de-comentarios');
		if (comentarios.length === 0) {
			lista.innerHTML = '<p class="aviso-vazio-secao">Nenhum comentário ainda.</p>';
			return;
		}
		lista.innerHTML = comentarios.map(comentario => {
			const podeExcluir = comentario.autor.id === usuario.id || souGestor();
			return `
				<li class="item-comentario" data-id="${comentario.id}">
					<div class="cabecalho-do-comentario">
						<span class="autor-do-comentario">${escaparHtml(comentario.autor.nome)}</span>
						<span class="quando">${formatarDataEHora(comentario.criadoEm)}</span>
						${podeExcluir ? '<button type="button" class="botao-texto botao-excluir-comentario">Excluir</button>' : ''}
					</div>
					${escaparHtml(comentario.texto)}
				</li>
			`;
		}).join('');

		lista.querySelectorAll('.botao-excluir-comentario').forEach(botao => {
			botao.addEventListener('click', async () => {
				try {
					await chamarApi('/comentarios/' + botao.closest('.item-comentario').dataset.id, { method: 'DELETE' });
					await carregarComentarios(tarefaId);
				} catch (erro) {
					exibirMensagem(erro.message, 'erro');
				}
			});
		});
	} catch (erro) {
		exibirMensagem(erro.message, 'erro');
	}
}

document.getElementById('botao-comentar').addEventListener('click', async () => {
	const campo = document.getElementById('novo-comentario');
	if (!campo.value.trim()) {
		return;
	}
	try {
		await chamarApi('/tarefas/' + tarefaEmEdicao.id + '/comentarios', {
			method: 'POST',
			body: { texto: campo.value }
		});
		campo.value = '';
		await carregarComentarios(tarefaEmEdicao.id);
	} catch (erro) {
		exibirMensagem(erro.message, 'erro');
	}
});

function formatarTamanhoDoArquivo(bytes) {
	if (bytes < 1024) {
		return bytes + ' B';
	}
	if (bytes < 1024 * 1024) {
		return (bytes / 1024).toFixed(0) + ' KB';
	}
	return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

async function carregarAnexos(tarefaId) {
	try {
		const anexos = await chamarApi('/tarefas/' + tarefaId + '/anexos');
		const lista = document.getElementById('lista-de-anexos');
		if (anexos.length === 0) {
			lista.innerHTML = '<p class="aviso-vazio-secao">Nenhum anexo ainda.</p>';
			return;
		}
		lista.innerHTML = anexos.map(anexo => {
			const podeExcluir = anexo.enviadoPor.id === usuario.id || souGestor();
			return `
				<li class="item-anexo" data-id="${anexo.id}">
					<span class="nome-do-anexo" title="${escaparHtml(anexo.nome)}">${escaparHtml(anexo.nome)}</span>
					<span class="tamanho-do-anexo">${formatarTamanhoDoArquivo(anexo.tamanho)}</span>
					<button type="button" class="botao-texto botao-baixar-anexo">Baixar</button>
					${podeExcluir ? '<button type="button" class="botao-texto botao-excluir-anexo">Excluir</button>' : ''}
				</li>
			`;
		}).join('');

		lista.querySelectorAll('.botao-baixar-anexo').forEach(botao => {
			botao.addEventListener('click', () => baixarAnexo(botao.closest('.item-anexo').dataset.id));
		});
		lista.querySelectorAll('.botao-excluir-anexo').forEach(botao => {
			botao.addEventListener('click', async () => {
				try {
					await chamarApi('/anexos/' + botao.closest('.item-anexo').dataset.id, { method: 'DELETE' });
					await carregarAnexos(tarefaId);
				} catch (erro) {
					exibirMensagem(erro.message, 'erro');
				}
			});
		});
	} catch (erro) {
		exibirMensagem(erro.message, 'erro');
	}
}

/** Baixa o anexo via fetch direto (com o token), pois a resposta e binaria, nao JSON. */
async function baixarAnexo(anexoId) {
	try {
		const resposta = await fetch('/api/anexos/' + anexoId + '/download', {
			headers: { Authorization: 'Bearer ' + localStorage.getItem('kanvox_token') }
		});
		if (!resposta.ok) {
			throw new Error('Não foi possível baixar o anexo.');
		}
		const disposicao = resposta.headers.get('Content-Disposition') || '';
		const nomeDoArquivo = (disposicao.match(/filename="(.+)"/) || [])[1] || 'arquivo';
		const url = URL.createObjectURL(await resposta.blob());
		const link = document.createElement('a');
		link.href = url;
		link.download = nomeDoArquivo;
		link.click();
		URL.revokeObjectURL(url);
	} catch (erro) {
		exibirMensagem(erro.message, 'erro');
	}
}

document.getElementById('botao-enviar-anexo').addEventListener('click', async () => {
	const input = document.getElementById('input-anexo');
	if (!input.files || input.files.length === 0) {
		exibirMensagem('Escolha um arquivo para anexar.', 'erro');
		return;
	}
	const arquivo = input.files[0];
	if (arquivo.size > 10 * 1024 * 1024) {
		exibirMensagem('O arquivo excede o limite de 10MB.', 'erro');
		return;
	}
	try {
		const formulario = new FormData();
		formulario.append('arquivo', arquivo);
		await chamarApi('/tarefas/' + tarefaEmEdicao.id + '/anexos', { method: 'POST', body: formulario });
		input.value = '';
		exibirMensagem('Anexo enviado!', 'sucesso');
		await carregarAnexos(tarefaEmEdicao.id);
	} catch (erro) {
		exibirMensagem(erro.message, 'erro');
	}
});

/** Linha do tempo da tarefa: criacao, edicoes e mudancas de coluna — somente leitura. */
async function carregarHistorico(tarefaId) {
	try {
		const historico = await chamarApi('/tarefas/' + tarefaId + '/historico');
		const lista = document.getElementById('lista-de-historico');
		if (historico.length === 0) {
			lista.innerHTML = '<p class="aviso-vazio-secao">Nenhuma atividade registrada ainda.</p>';
			return;
		}
		lista.innerHTML = historico.map(evento => `
			<li class="item-historico">
				<span class="autor-do-historico">${escaparHtml(evento.autor.nome)}</span>
				${escaparHtml(evento.descricao)}
				<span class="quando">${formatarDataEHora(evento.criadoEm)}</span>
			</li>
		`).join('');
	} catch (erro) {
		exibirMensagem(erro.message, 'erro');
	}
}

/* ---------- membros (RF-01.4 / RF-01.5) ---------- */

function renderizarMembros() {
	const rotulos = { GESTOR: 'Gestor', MEMBRO: 'Membro', OBSERVADOR: 'Observador' };
	const lista = document.getElementById('lista-de-membros');
	lista.innerHTML = membros.map(membro => {
		const pendente = membro.situacao === 'PENDENTE';
		return `
			<li>
				<strong>${escaparHtml(membro.usuario.nome)}</strong>
				<span class="texto-suave">${escaparHtml(membro.usuario.email)}</span>
				<span class="selo selo-${membro.papelNoProjeto.toLowerCase()}">${rotulos[membro.papelNoProjeto]}</span>
				${pendente ? '<span class="selo selo-encerrado">Convite pendente</span>' : ''}
				<div class="espacador"></div>
				${souGestor() && membro.papelNoProjeto !== 'GESTOR' && projeto.status === 'ATIVO'
					? `<button type="button" class="botao-perigo botao-compacto botao-remover" data-usuario="${membro.usuario.id}">
						${pendente ? 'Cancelar convite' : 'Remover'}</button>`
					: ''}
			</li>
		`;
	}).join('');

	lista.querySelectorAll('.botao-remover').forEach(botao => {
		botao.addEventListener('click', async () => {
			if (!confirm('Remover este membro do projeto?')) {
				return;
			}
			try {
				await chamarApi('/projetos/' + projetoId + '/membros/' + botao.dataset.usuario, { method: 'DELETE' });
				exibirMensagem('Membro removido.', 'sucesso');
				await carregarVisaoGeral();
			} catch (erro) {
				exibirMensagem(erro.message, 'erro');
			}
		});
	});
}

document.getElementById('botao-convidar').addEventListener('click', async () => {
	try {
		await chamarApi('/projetos/' + projetoId + '/membros', {
			method: 'POST',
			body: {
				email: document.getElementById('convite-email').value,
				papel: document.getElementById('convite-papel').value
			}
		});
		document.getElementById('convite-email').value = '';
		exibirMensagem('Membro convidado!', 'sucesso');
		await carregarVisaoGeral();
	} catch (erro) {
		exibirMensagem(erro.message, 'erro');
	}
});

document.getElementById('botao-sair-do-projeto').addEventListener('click', async () => {
	if (!confirm('Sair deste projeto? Você perderá o acesso a ele.')) {
		return;
	}
	try {
		await chamarApi('/projetos/' + projetoId + '/sair', { method: 'POST' });
		window.location.href = '/projetos.html';
	} catch (erro) {
		exibirMensagem(erro.message, 'erro');
	}
});

/* ---------- edicao e encerramento do projeto (RF-02.2) ---------- */

const dialogoEditarProjeto = document.getElementById('dialogo-editar-projeto');

document.getElementById('botao-editar-projeto').addEventListener('click', () => {
	document.getElementById('editar-nome').value = projeto.nome;
	document.getElementById('editar-descricao').value = projeto.descricao || '';
	dialogoEditarProjeto.showModal();
});

document.getElementById('botao-cancelar-edicao').addEventListener('click', () => dialogoEditarProjeto.close());

document.getElementById('formulario-editar-projeto').addEventListener('submit', async (evento) => {
	evento.preventDefault();
	try {
		await chamarApi('/projetos/' + projetoId, {
			method: 'PUT',
			body: {
				nome: document.getElementById('editar-nome').value,
				descricao: document.getElementById('editar-descricao').value
			}
		});
		dialogoEditarProjeto.close();
		await carregarVisaoGeral();
	} catch (erro) {
		exibirMensagem(erro.message, 'erro');
	}
});

document.getElementById('botao-encerrar-projeto').addEventListener('click', async () => {
	if (!confirm('Encerrar este projeto? O quadro ficará somente leitura.')) {
		return;
	}
	try {
		await chamarApi('/projetos/' + projetoId + '/encerrar', { method: 'PUT' });
		await carregarVisaoGeral();
		renderizarQuadro();
	} catch (erro) {
		exibirMensagem(erro.message, 'erro');
	}
});

/* ---------- relatorios (RF-04) e narracao por voz (RF-05) ---------- */

const dialogoRelatorio = document.getElementById('dialogo-relatorio');

async function carregarRelatorios() {
	const relatorios = await chamarApi('/projetos/' + projetoId + '/relatorios');
	const lista = document.getElementById('lista-de-relatorios');
	document.getElementById('aviso-sem-relatorios').hidden = relatorios.length > 0;
	lista.innerHTML = relatorios.map(relatorio => `
		<article class="cartao-relatorio" data-id="${relatorio.id}">
			<div class="cabecalho-do-relatorio">
				<div>
					<strong>Relatório de ${formatarDataEHora(relatorio.geradoEm)}</strong>
					<span class="texto-suave"> — gerado por ${escaparHtml(relatorio.geradoPor.nome)}</span>
				</div>
				<div class="espacador"></div>
				<button type="button" class="botao-secundario botao-compacto botao-baixar-pdf">Baixar PDF</button>
			</div>
			<pre>${escaparHtml(relatorio.conteudo)}</pre>
			${relatorio.transcricaoAudio
				? `<div class="narracao">${ICONE_MICROFONE}<strong>Observação narrada:</strong> ${escaparHtml(relatorio.transcricaoAudio)}</div>`
				: ''}
		</article>
	`).join('');

	lista.querySelectorAll('.botao-baixar-pdf').forEach(botao => {
		botao.addEventListener('click', () => baixarPdfDoRelatorio(botao.closest('.cartao-relatorio').dataset.id));
	});
}

/** Baixa o PDF do relatorio via fetch direto (com o token), pois a resposta e binaria (RF-04.4). */
async function baixarPdfDoRelatorio(relatorioId) {
	try {
		const resposta = await fetch('/api/relatorios/' + relatorioId + '/pdf', {
			headers: { Authorization: 'Bearer ' + localStorage.getItem('kanvox_token') }
		});
		if (!resposta.ok) {
			throw new Error('Não foi possível baixar o PDF do relatório.');
		}
		const url = URL.createObjectURL(await resposta.blob());
		const link = document.createElement('a');
		link.href = url;
		link.download = 'relatorio-' + relatorioId + '.pdf';
		link.click();
		URL.revokeObjectURL(url);
	} catch (erro) {
		exibirMensagem(erro.message, 'erro');
	}
}

document.getElementById('botao-novo-relatorio').addEventListener('click', () => {
	audioGravado = null;
	document.getElementById('texto-da-narracao').value = '';
	document.getElementById('audio-gravado').hidden = true;
	document.getElementById('botao-transcrever').hidden = true;
	dialogoRelatorio.showModal();
});

document.getElementById('botao-cancelar-relatorio').addEventListener('click', () => dialogoRelatorio.close());

/** Inicia ou para a gravacao pelo microfone (RF-05.1 — MediaRecorder do navegador). */
document.getElementById('botao-gravar').addEventListener('click', async () => {
	if (gravador && gravador.state === 'recording') {
		gravador.stop();
		return;
	}
	try {
		const microfone = await navigator.mediaDevices.getUserMedia({ audio: true });
		const pedacos = [];
		gravador = new MediaRecorder(microfone);
		gravador.ondataavailable = (evento) => pedacos.push(evento.data);
		gravador.onstop = () => {
			microfone.getTracks().forEach(faixa => faixa.stop());
			audioGravado = new Blob(pedacos, { type: 'audio/webm' });
			const reprodutor = document.getElementById('audio-gravado');
			reprodutor.src = URL.createObjectURL(audioGravado);
			reprodutor.hidden = false;
			document.getElementById('botao-transcrever').hidden = false;
			document.getElementById('indicador-de-gravacao').hidden = true;
			document.getElementById('botao-gravar').innerHTML = ICONE_MICROFONE + 'Gravar de novo';
		};
		gravador.start();
		document.getElementById('indicador-de-gravacao').hidden = false;
		document.getElementById('botao-gravar').innerHTML = ICONE_PARAR + 'Parar gravação';
	} catch (erro) {
		exibirMensagem('Não foi possível acessar o microfone. Verifique a permissão do navegador.', 'erro');
	}
});

/** Envia o audio para transcricao (RF-05.2) e poe o texto na caixa de revisao (RF-05.5). */
document.getElementById('botao-transcrever').addEventListener('click', async () => {
	const indicador = document.getElementById('indicador-de-transcricao');
	indicador.hidden = false;
	document.getElementById('botao-transcrever').disabled = true;
	try {
		const formulario = new FormData();
		formulario.append('audio', audioGravado, 'gravacao.webm');
		const resposta = await chamarApi('/projetos/' + projetoId + '/transcricoes', {
			method: 'POST',
			body: formulario
		});
		document.getElementById('texto-da-narracao').value = resposta.texto;
		exibirMensagem('Transcrição pronta! Revise o texto antes de gerar o relatório.', 'sucesso');
	} catch (erro) {
		// RF-05.4: a falha na transcricao nao impede a geracao do relatorio
		exibirMensagem(erro.message, 'erro');
	} finally {
		indicador.hidden = true;
		document.getElementById('botao-transcrever').disabled = false;
	}
});

document.getElementById('botao-gerar-relatorio').addEventListener('click', async () => {
	try {
		const narracao = document.getElementById('texto-da-narracao').value.trim();
		await chamarApi('/projetos/' + projetoId + '/relatorios', {
			method: 'POST',
			body: narracao ? { transcricaoAudio: narracao } : {}
		});
		dialogoRelatorio.close();
		exibirMensagem('Relatório gerado!', 'sucesso');
		await carregarRelatorios();
	} catch (erro) {
		exibirMensagem(erro.message, 'erro');
	}
});

/* ---------- desempenho: carga de trabalho e eficiencia da equipe (Gestor) ---------- */
/* Painel so de leitura: nada aqui e escrito pelo usuario, so agregado a
   partir das tarefas do projeto (responsaveis, status, prazo, conclusao). */

/** "2,3 dias" — nulo (sem tarefas concluidas ainda) vira travessao. */
function formatarDias(dias) {
	return dias === null || dias === undefined ? '—' : dias.toFixed(1).replace('.', ',') + ' dias';
}

/** "+3,0 dias adiantado" / "−1,5 dias de atraso" / "no prazo exato". */
function formatarEficiencia(dias) {
	if (dias === null || dias === undefined) {
		return '<span class="texto-suave">—</span>';
	}
	const arredondado = Math.abs(dias).toFixed(1).replace('.', ',');
	if (dias > 0.05) {
		return '<span class="eficiencia-adiantada">+' + arredondado + ' dias adiantado</span>';
	}
	if (dias < -0.05) {
		return '<span class="eficiencia-atrasada">−' + arredondado + ' dias de atraso</span>';
	}
	return 'no prazo exato';
}

async function carregarDesempenho() {
	try {
		const dados = await chamarApi('/projetos/' + projetoId + '/desempenho');

		document.getElementById('resumo-progresso').textContent = dados.resumo.progresso + '%';
		document.getElementById('resumo-no-prazo').textContent =
			dados.resumo.percentualNoPrazo === null ? '—' : dados.resumo.percentualNoPrazo + '%';
		document.getElementById('resumo-bloqueadas').textContent = dados.resumo.tarefasBloqueadas;

		document.getElementById('aviso-sem-membros-desempenho').hidden = dados.porMembro.length > 0;
		document.getElementById('corpo-da-tabela-de-desempenho').innerHTML = dados.porMembro.map(linha => `
			<tr>
				<td>${escaparHtml(linha.usuario.nome)}</td>
				<td>${linha.concluidas}</td>
				<td>${linha.emAberto}</td>
				<td>${linha.atrasadas > 0 ? '<span class="prazo-vencido">' + linha.atrasadas + '</span>' : linha.atrasadas}</td>
				<td>${formatarDias(linha.tempoMedioDeConclusaoEmDias)}</td>
				<td>${formatarEficiencia(linha.eficienciaMediaEmDias)}</td>
			</tr>
		`).join('');
	} catch (erro) {
		exibirMensagem(erro.message, 'erro');
	}
}

/* ---------- inicializacao ---------- */

(async function iniciar() {
	try {
		await carregarVisaoGeral();
		await carregarTarefas();
		await carregarRelatorios();
	} catch (erro) {
		exibirMensagem(erro.message, 'erro');
	}
})();
