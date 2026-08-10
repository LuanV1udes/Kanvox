/* ============================================================
   tema.js — alternancia entre o tema claro e o tema escuro

   Este arquivo e carregado no <head> de todas as paginas, antes
   do corpo ser desenhado: assim o tema ja e aplicado no primeiro
   quadro e a tela nao "pisca" clara antes de ficar escura.

   O tema escolhido fica no localStorage. Enquanto o usuario nunca
   escolheu, vale a preferencia configurada no sistema operacional
   (prefers-color-scheme).
   ============================================================ */

const CHAVE_DO_TEMA = 'kanvox_tema';

/** Tema configurado no sistema operacional do usuario. */
function temaDoSistema() {
	const escuro = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
	return escuro ? 'escuro' : 'claro';
}

/** Tema em uso: o que o usuario escolheu ou, se nunca escolheu, o do sistema. */
function temaAtual() {
	return localStorage.getItem(CHAVE_DO_TEMA) || temaDoSistema();
}

/** Aplica o tema na pagina. Todo o resto e feito pelo CSS, via [data-tema]. */
function aplicarTema(tema) {
	document.documentElement.setAttribute('data-tema', tema);
}

/** Troca claro <-> escuro e guarda a escolha para as proximas visitas. */
function alternarTema() {
	aplicarTema(temaAtual() === 'escuro' ? 'claro' : 'escuro');
	localStorage.setItem(CHAVE_DO_TEMA, document.documentElement.getAttribute('data-tema'));
}

/**
 * HTML do botao que alterna o tema. Fica em um so lugar porque o botao
 * aparece em dois contextos: na barra lateral das paginas internas
 * (montada por navegacao.js) e na tela de entrada.
 * O CSS mostra o sol ou a lua conforme o tema em uso.
 */
function botaoDeTema(classesExtras) {
	return `
		<button type="button" class="botao-icone botao-de-tema ${classesExtras || ''}"
			title="Alternar tema claro e escuro" aria-label="Alternar tema claro e escuro">
			<svg class="icone-lua" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"
				stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
				<path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"></path>
			</svg>
			<svg class="icone-sol" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"
				stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
				<circle cx="12" cy="12" r="4"></circle>
				<path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M6.3 17.7l-1.4 1.4M19.1 4.9l-1.4 1.4"></path>
			</svg>
		</button>
	`;
}

// O clique e ouvido no documento inteiro: assim funciona tanto para o botao
// da tela de entrada quanto para o do cabecalho, que so existe depois.
document.addEventListener('click', (evento) => {
	if (evento.target.closest('.botao-de-tema')) {
		alternarTema();
	}
});

// Na tela de entrada nao ha cabecalho: o botao entra no espaco reservado a ele.
document.addEventListener('DOMContentLoaded', () => {
	const area = document.getElementById('area-do-tema');
	if (area) {
		area.innerHTML = botaoDeTema('alternador-fixo');
	}
});

// aplica o tema imediatamente, ainda durante a leitura do <head>
aplicarTema(temaAtual());
