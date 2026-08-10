/* ============================================================
   navegacao.js — barra lateral das paginas internas

   A barra reune, em um so lugar: a marca, os links de navegacao,
   o sino de notificacoes (RF-06.4), o botao de tema e a saida da
   conta. Na pagina do projeto ela tambem troca a secao exibida
   (quadro, membros, relatorios) sem recarregar a pagina.

   O sino e atualizado pelo mesmo ciclo de polling do restante da
   tela, a cada 5 segundos (RF-03.6 / RNF-01).
   ============================================================ */

let notificacoes = [];
let painelAberto = false;

/* Icones da navegacao, desenhados aqui mesmo em SVG — sem biblioteca
   externa e sem arquivo de imagem, seguindo a decisao de manter o
   frontend sem dependencias. */
const ICONES = {
	projetos: '<rect x="3" y="3" width="7" height="7" rx="1"></rect><rect x="14" y="3" width="7" height="7" rx="1"></rect><rect x="14" y="14" width="7" height="7" rx="1"></rect><rect x="3" y="14" width="7" height="7" rx="1"></rect>',
	quadro: '<rect x="3" y="3" width="18" height="18" rx="2"></rect><path d="M9 3v18M15 3v18"></path>',
	membros: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"></path><circle cx="9" cy="7" r="4"></circle><path d="M22 21v-2a4 4 0 0 0-3-3.87"></path><path d="M16 3.13a4 4 0 0 1 0 7.75"></path>',
	relatorios: '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><path d="M14 2v6h6M16 13H8M16 17H8"></path>'
};

function icone(nome) {
	return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"
		stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICONES[nome]}</svg>`;
}

/** Iniciais do nome, usadas no circulo do usuario. */
function iniciaisDoNome(nome) {
	const partes = String(nome || '').trim().split(/\s+/);
	const primeira = partes[0] ? partes[0].charAt(0) : '';
	const ultima = partes.length > 1 ? partes[partes.length - 1].charAt(0) : '';
	return (primeira + ultima).toUpperCase();
}

/**
 * Monta a barra lateral dentro do elemento <aside id="barra-lateral">.
 *
 * itensDoProjeto (opcional): lista de { visao, rotulo, icone } com as
 *   secoes da pagina do projeto. Cada item vira um botao que mostra a
 *   secao correspondente da pagina.
 * aoTrocarDeVisao (opcional): funcao chamada com o nome da visao
 *   escolhida, para a pagina reagir a troca.
 */
function montarNavegacao(itensDoProjeto, aoTrocarDeVisao) {
	const usuario = usuarioLogado();
	const barra = document.getElementById('barra-lateral');
	const itens = itensDoProjeto || [];
	barra.className = 'barra-lateral';
	barra.innerHTML = `
		<a class="logotipo" href="/projetos.html">
			<span class="marca-simbolo" aria-hidden="true">K</span>Kanvox
		</a>

		<nav class="navegacao">
			<p class="rotulo-do-grupo">Geral</p>
			<div class="grupo-de-navegacao">
				<a class="item-de-navegacao ${itens.length === 0 ? 'ativo' : ''}" href="/projetos.html">
					${icone('projetos')}<span>Meus projetos</span>
				</a>
			</div>
			${itens.length === 0 ? '' : `
				<p class="rotulo-do-grupo">Projeto</p>
				<div class="grupo-de-navegacao">
					${itens.map((item, indice) => `
						<button type="button" class="item-de-navegacao ${indice === 0 ? 'ativo' : ''}" data-visao="${item.visao}">
							${icone(item.icone)}<span>${escaparHtml(item.rotulo)}</span>
						</button>
					`).join('')}
				</div>
			`}
		</nav>

		<div class="rodape-da-barra">
			<div class="usuario">
				<span class="avatar" aria-hidden="true">${escaparHtml(iniciaisDoNome(usuario.nome))}</span>
				<span class="nome-do-usuario">${escaparHtml(usuario.nome)}</span>
			</div>
			<div class="acoes-da-barra">
				<div class="notificacoes">
					<button type="button" class="botao-icone" id="botao-sino" title="Notificações" aria-label="Notificações">
						<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"
							stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
							<path d="M18 8a6 6 0 1 0-12 0c0 7-3 9-3 9h18s-3-2-3-9"></path>
							<path d="M13.7 21a2 2 0 0 1-3.4 0"></path>
						</svg>
						<span class="contador-nao-lidas" id="contador-nao-lidas" hidden></span>
					</button>
					<div class="painel-notificacoes" id="painel-notificacoes" hidden>
						<header>
							<strong>Notificações</strong>
							<button type="button" class="botao-texto" id="botao-marcar-todas">Marcar todas como lidas</button>
						</header>
						<div id="lista-de-notificacoes"></div>
					</div>
				</div>
				${botaoDeTema()}
				<div class="espacador"></div>
				<button type="button" class="botao-secundario botao-compacto" id="botao-sair">Sair</button>
			</div>
		</div>
	`;

	document.getElementById('botao-sair').addEventListener('click', sairDaConta);
	document.getElementById('botao-sino').addEventListener('click', alternarPainelDeNotificacoes);
	document.getElementById('botao-marcar-todas').addEventListener('click', marcarTodasComoLidas);

	// cada item do projeto troca a secao exibida na pagina
	barra.querySelectorAll('.item-de-navegacao[data-visao]').forEach(item => {
		item.addEventListener('click', () => {
			mostrarVisao(item.dataset.visao);
			if (aoTrocarDeVisao) {
				aoTrocarDeVisao(item.dataset.visao);
			}
		});
	});

	// fecha o painel de notificacoes ao clicar fora dele
	document.addEventListener('click', (evento) => {
		if (painelAberto && !evento.target.closest('.notificacoes')) {
			alternarPainelDeNotificacoes();
		}
	});

	atualizarNotificacoes();
	setInterval(atualizarNotificacoes, 5000);
}

/**
 * Mostra a secao pedida e esconde as demais. Cada secao da pagina
 * carrega o atributo data-conteudo-da-visao com o seu nome.
 */
function mostrarVisao(nome) {
	document.querySelectorAll('.item-de-navegacao[data-visao]').forEach(item => {
		item.classList.toggle('ativo', item.dataset.visao === nome);
	});
	document.querySelectorAll('[data-conteudo-da-visao]').forEach(secao => {
		secao.hidden = secao.dataset.conteudoDaVisao !== nome;
	});
	window.scrollTo(0, 0);
}

function alternarPainelDeNotificacoes() {
	painelAberto = !painelAberto;
	document.getElementById('painel-notificacoes').hidden = !painelAberto;
}

async function atualizarNotificacoes() {
	try {
		notificacoes = await chamarApi('/notificacoes');
	} catch (erro) {
		return; // falha silenciosa: o polling tenta de novo em 5s
	}
	const naoLidas = notificacoes.filter(n => !n.lida).length;
	const contador = document.getElementById('contador-nao-lidas');
	contador.hidden = naoLidas === 0;
	contador.textContent = naoLidas;

	const lista = document.getElementById('lista-de-notificacoes');
	if (notificacoes.length === 0) {
		lista.innerHTML = '<p class="texto-suave aviso-vazio">Nenhuma notificação por enquanto.</p>';
		return;
	}
	lista.innerHTML = notificacoes.map(n => `
		<div class="item-notificacao ${n.lida ? '' : 'nao-lida'}" data-id="${n.id}">
			${escaparHtml(n.mensagem)}
			<span class="quando">${formatarDataEHora(n.criadoEm)}</span>
		</div>
	`).join('');

	// clicar em uma notificacao nao lida marca como lida
	lista.querySelectorAll('.item-notificacao.nao-lida').forEach(item => {
		item.addEventListener('click', async () => {
			await chamarApi('/notificacoes/' + item.dataset.id + '/lida', { method: 'PUT' });
			atualizarNotificacoes();
		});
	});
}

async function marcarTodasComoLidas() {
	await chamarApi('/notificacoes/lidas', { method: 'PUT' });
	atualizarNotificacoes();
}
