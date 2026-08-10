/* ============================================================
   projeto.js — pagina do projeto: quadro Kanban (RF-03),
   membros (RF-01), relatorios (RF-04) e narracao por voz (RF-05).
   O quadro e as notificacoes sao atualizados por polling a cada
   5 segundos (RF-03.6 / RNF-01), sem WebSocket — decisao de projeto.
   ============================================================ */

exigirSessao();

// As tres secoes da pagina viram itens da barra lateral: a primeira
// (o quadro) e a que aparece ao abrir o projeto.
montarNavegacao([
	{ visao: 'quadro', rotulo: 'Quadro', icone: 'quadro' },
	{ visao: 'membros', rotulo: 'Membros', icone: 'membros' },
	{ visao: 'relatorios', rotulo: 'Relatórios', icone: 'relatorios' }
], (visao) => {
	// ao voltar para os relatorios, busca a lista de novo (pode ter mudado)
	if (visao === 'relatorios') {
		carregarRelatorios().catch(erro => exibirMensagem(erro.message, 'erro'));
	}
});

const projetoId = new URLSearchParams(window.location.search).get('id');
const usuario = usuarioLogado();

const COLUNAS = [
	{ status: 'A_FAZER', titulo: 'A Fazer' },
	{ status: 'EM_ANDAMENTO', titulo: 'Em Andamento' },
	{ status: 'BLOQUEADO', titulo: 'Bloqueado' },
	{ status: 'CONCLUIDO', titulo: 'Concluído' }
];

let projeto = null;
let membros = [];
let tarefas = [];
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

	ajustarPermissoesDaTela();
	renderizarMembros();
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
}

async function carregarTarefas() {
	const novas = await chamarApi('/projetos/' + projetoId + '/tarefas');
	// so redesenha o quadro se algo mudou (evita piscadas no polling)
	if (JSON.stringify(novas) !== JSON.stringify(tarefas)) {
		tarefas = novas;
		renderizarQuadro();
	}
}

setInterval(() => {
	const algumDialogoAberto = document.querySelector('dialog[open]');
	if (!arrastando && !algumDialogoAberto) {
		carregarTarefas().catch(() => {});
	}
}, 5000);

/* ---------- quadro kanban (RF-03) ---------- */

function renderizarQuadro() {
	const quadro = document.getElementById('quadro');
	quadro.innerHTML = COLUNAS.map(coluna => {
		const tarefasDaColuna = tarefas.filter(tarefa => tarefa.status === coluna.status);
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
				onEnd: aoSoltarCartao
			});
		});
	}
}

function montarCartaoDeTarefa(tarefa) {
	// membro so pode mover as proprias tarefas (RF-03.3)
	const minhaTarefa = tarefa.responsavel && tarefa.responsavel.id === usuario.id;
	const arrastavel = souGestor() || minhaTarefa;
	const prazoVencido = tarefa.prazo && tarefa.status !== 'CONCLUIDO'
		&& tarefa.prazo < new Date().toISOString().slice(0, 10);
	return `
		<li class="cartao-tarefa ${arrastavel ? '' : 'nao-arrastavel'}" data-id="${tarefa.id}">
			<div class="titulo-da-tarefa">${escaparHtml(tarefa.titulo)}</div>
			<div class="detalhes-da-tarefa">
				<span>${tarefa.responsavel ? escaparHtml(tarefa.responsavel.nome) : 'Sem responsável'}</span>
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
		tarefas = [];
		carregarTarefas().catch(() => {});
		carregarVisaoGeral().catch(() => {});
	}
}

/* ---------- dialogo de tarefa ---------- */

const dialogoTarefa = document.getElementById('dialogo-tarefa');

function preencherSelectDeResponsaveis(selecionadoId) {
	const select = document.getElementById('tarefa-responsavel');
	const opcoes = membros
		.filter(membro => membro.papelNoProjeto !== 'OBSERVADOR')
		.map(membro => `<option value="${membro.usuario.id}" ${membro.usuario.id === selecionadoId ? 'selected' : ''}>
			${escaparHtml(membro.usuario.nome)}</option>`);
	select.innerHTML = '<option value="">Sem responsável</option>' + opcoes.join('');
}

function abrirDialogoDeTarefa(tarefa) {
	tarefaEmEdicao = tarefa || null;
	const criando = !tarefa;
	document.getElementById('titulo-do-dialogo-tarefa').textContent = criando ? 'Nova tarefa' : 'Tarefa';
	document.getElementById('tarefa-titulo').value = criando ? '' : tarefa.titulo;
	document.getElementById('tarefa-descricao').value = criando ? '' : (tarefa.descricao || '');
	document.getElementById('tarefa-prazo').value = criando ? '' : (tarefa.prazo || '');

	// o select de responsavel so aparece para o Gestor (membro cria para si, RF-03.3)
	document.getElementById('rotulo-responsavel').hidden = !souGestor();
	if (souGestor()) {
		preencherSelectDeResponsaveis(tarefa && tarefa.responsavel ? tarefa.responsavel.id : null);
	}

	// permissao de edicao: gestor edita tudo; membro so as proprias tarefas
	const minhaTarefa = tarefa && tarefa.responsavel && tarefa.responsavel.id === usuario.id;
	const podeEditar = podeEscrever() && (souGestor() || minhaTarefa);
	['tarefa-titulo', 'tarefa-descricao', 'tarefa-prazo', 'tarefa-responsavel'].forEach(id => {
		document.getElementById(id).disabled = !podeEditar;
	});
	document.getElementById('botao-salvar-tarefa').hidden = !podeEditar;
	document.getElementById('botao-excluir-tarefa').hidden = !(souGestor() && !criando && projeto.status === 'ATIVO');

	dialogoTarefa.showModal();
}

document.getElementById('botao-nova-tarefa').addEventListener('click', () => abrirDialogoDeTarefa(null));
document.getElementById('botao-cancelar-tarefa').addEventListener('click', () => dialogoTarefa.close());

document.getElementById('formulario-tarefa').addEventListener('submit', async (evento) => {
	evento.preventDefault();
	const corpo = {
		titulo: document.getElementById('tarefa-titulo').value,
		descricao: document.getElementById('tarefa-descricao').value,
		prazo: document.getElementById('tarefa-prazo').value || null
	};
	if (souGestor()) {
		const responsavelId = document.getElementById('tarefa-responsavel').value;
		corpo.responsavel = responsavelId ? { id: Number(responsavelId) } : null;
	}
	try {
		if (tarefaEmEdicao) {
			await chamarApi('/tarefas/' + tarefaEmEdicao.id, { method: 'PUT', body: corpo });
		} else {
			await chamarApi('/projetos/' + projetoId + '/tarefas', { method: 'POST', body: corpo });
		}
		dialogoTarefa.close();
		tarefas = [];
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
		tarefas = [];
		await carregarTarefas();
		await carregarVisaoGeral();
	} catch (erro) {
		exibirMensagem(erro.message, 'erro');
	}
});

/* ---------- membros (RF-01.4 / RF-01.5) ---------- */

function renderizarMembros() {
	const rotulos = { GESTOR: 'Gestor', MEMBRO: 'Membro', OBSERVADOR: 'Observador' };
	const lista = document.getElementById('lista-de-membros');
	lista.innerHTML = membros.map(membro => `
		<li>
			<strong>${escaparHtml(membro.usuario.nome)}</strong>
			<span class="texto-suave">${escaparHtml(membro.usuario.email)}</span>
			<span class="selo selo-${membro.papelNoProjeto.toLowerCase()}">${rotulos[membro.papelNoProjeto]}</span>
			<div class="espacador"></div>
			${souGestor() && membro.papelNoProjeto !== 'GESTOR' && projeto.status === 'ATIVO'
				? `<button type="button" class="botao-perigo botao-compacto botao-remover" data-usuario="${membro.usuario.id}">Remover</button>`
				: ''}
		</li>
	`).join('');

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
	document.getElementById('aviso-sem-relatorios').hidden = relatorios.length > 0;
	document.getElementById('lista-de-relatorios').innerHTML = relatorios.map(relatorio => `
		<article class="cartao-relatorio">
			<strong>Relatório de ${formatarDataEHora(relatorio.geradoEm)}</strong>
			<span class="texto-suave"> — gerado por ${escaparHtml(relatorio.geradoPor.nome)}</span>
			<pre>${escaparHtml(relatorio.conteudo)}</pre>
			${relatorio.transcricaoAudio
				? `<div class="narracao">🎙️ <strong>Observação narrada:</strong> ${escaparHtml(relatorio.transcricaoAudio)}</div>`
				: ''}
		</article>
	`).join('');
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
			document.getElementById('botao-gravar').textContent = '🎙️ Gravar de novo';
		};
		gravador.start();
		document.getElementById('indicador-de-gravacao').hidden = false;
		document.getElementById('botao-gravar').textContent = '⏹️ Parar gravação';
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
