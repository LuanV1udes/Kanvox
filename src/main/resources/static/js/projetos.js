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
