/* ============================================================
   projetos.js — lista de projetos do usuario (RF-02)
   ============================================================ */

exigirSessao();
montarNavegacao();

const gradeDeProjetos = document.getElementById('grade-de-projetos');
const avisoSemProjetos = document.getElementById('aviso-sem-projetos');
const dialogoProjeto = document.getElementById('dialogo-projeto');

async function carregarProjetos() {
	try {
		const projetos = await chamarApi('/projetos');
		avisoSemProjetos.hidden = projetos.length > 0;
		gradeDeProjetos.innerHTML = projetos.map(projeto => `
			<div class="cartao-projeto" data-id="${projeto.id}">
				<div class="cartao-projeto-topo">
					<h3>${escaparHtml(projeto.nome)}</h3>
					<div class="espacador"></div>
					<span class="selo ${projeto.status === 'ATIVO' ? 'selo-ativo' : 'selo-encerrado'}">${projeto.status === 'ATIVO' ? 'Ativo' : 'Encerrado'}</span>
				</div>
				<p>${escaparHtml(projeto.descricao || 'Sem descrição.')}</p>
			</div>
		`).join('');

		gradeDeProjetos.querySelectorAll('.cartao-projeto').forEach(cartao => {
			cartao.addEventListener('click', () => {
				window.location.href = '/projeto.html?id=' + cartao.dataset.id;
			});
		});
	} catch (erro) {
		exibirMensagem(erro.message, 'erro');
	}
}

/* ---------- convites pendentes (RF-01.4) ---------- */

const listaDeConvites = document.getElementById('lista-de-convites');

async function carregarConvites() {
	try {
		const convites = await chamarApi('/convites');
		listaDeConvites.hidden = convites.length === 0;
		listaDeConvites.innerHTML = convites.map(convite => {
			const rotulos = { MEMBRO: 'Membro', OBSERVADOR: 'Observador' };
			return `
				<div class="cartao-convite" data-id="${convite.id}">
					<div>
						<strong>${escaparHtml(convite.convidadoPor)}</strong> convidou você para
						<strong>${escaparHtml(convite.projeto.nome)}</strong>
						<p class="texto-suave">Papel: ${rotulos[convite.papelNoProjeto]}</p>
					</div>
					<div class="acoes-do-convite">
						<button type="button" class="botao-secundario botao-compacto botao-recusar-convite">Recusar</button>
						<button type="button" class="botao-principal botao-compacto botao-aceitar-convite">Aceitar</button>
					</div>
				</div>
			`;
		}).join('');

		listaDeConvites.querySelectorAll('.botao-aceitar-convite').forEach(botao => {
			botao.addEventListener('click', async () => {
				const conviteId = botao.closest('.cartao-convite').dataset.id;
				try {
					await chamarApi('/convites/' + conviteId + '/aceitar', { method: 'POST' });
					exibirMensagem('Convite aceito!', 'sucesso');
					await carregarConvites();
					await carregarProjetos();
				} catch (erro) {
					exibirMensagem(erro.message, 'erro');
				}
			});
		});
		listaDeConvites.querySelectorAll('.botao-recusar-convite').forEach(botao => {
			botao.addEventListener('click', async () => {
				const conviteId = botao.closest('.cartao-convite').dataset.id;
				try {
					await chamarApi('/convites/' + conviteId + '/recusar', { method: 'POST' });
					exibirMensagem('Convite recusado.', 'sucesso');
					await carregarConvites();
				} catch (erro) {
					exibirMensagem(erro.message, 'erro');
				}
			});
		});
	} catch (erro) {
		exibirMensagem(erro.message, 'erro');
	}
}

document.getElementById('botao-novo-projeto').addEventListener('click', () => {
	document.getElementById('formulario-projeto').reset();
	dialogoProjeto.showModal();
});

document.getElementById('botao-cancelar-projeto').addEventListener('click', () => dialogoProjeto.close());

document.getElementById('formulario-projeto').addEventListener('submit', async (evento) => {
	evento.preventDefault();
	try {
		await chamarApi('/projetos', {
			method: 'POST',
			body: {
				nome: document.getElementById('projeto-nome').value,
				descricao: document.getElementById('projeto-descricao').value
			}
		});
		dialogoProjeto.close();
		exibirMensagem('Projeto criado! Você é o Gestor dele.', 'sucesso');
		carregarProjetos();
	} catch (erro) {
		exibirMensagem(erro.message, 'erro');
	}
});

carregarProjetos();
carregarConvites();
// convites podem chegar a qualquer momento; revisita a lista no mesmo ritmo do polling geral
setInterval(carregarConvites, 5000);
