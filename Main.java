
/* Disciplina: Computacao Concorrente */
/* Prof.: Silvana Rossetto */
/* Trabalho 2 */
/* Estudantes: Vinícius Garcia (115.039.031)
/*             Rafael Katopodis (115.021.282)
/* -------------------------------------------------------------------*/

import java.util.*;
import java.io.*;

//--------------------------------------------------------
//gerencia o mapa de assentos. monitor da classe usuario
class Assentos {
	//classe base com métodos básicos de validação de alocação/desalocação
	class Assento {
		int id;
		boolean livre;

		Assento() {
			this.id = 0;
			this.livre = true;
		}

		synchronized boolean aloca(int id) {
			if (!this.livre) return false;

			this.id = id;
			this.livre = false;
			return true;
		}

		synchronized boolean desaloca(int id) {
			if (this.livre || this.id != id) return false;

			this.id = 0;
			this.livre = true;
			return true;
		}
	}

	Assento[] vetor; //mapa de assentos
	List<Integer> assentosLivres;
	int tamanho;
	int leitores,
		escritoresEspera;
	boolean escritor;

	//no construtor, o mapa de assentos é preenchido com assentos livres
	//e o mapa de assentos livres recebe todos os assentos
	public Assentos(int tamanho) {
		this.tamanho = tamanho;
		this.leitores = this.escritoresEspera = 0;
		this.escritor = false;

		assentosLivres = new ArrayList<Integer>();
		this.vetor = new Assento[tamanho];
		for (int i = 0; i < tamanho; i++) {
			Assento assento = new Assento();
			this.vetor[i] = assento;
			assentosLivres.add(i);
		}
	}

	public int getTamanho() {
		return this.tamanho;
	}

	//retorna string com mapa de assentos
	public String visualizaAssentos() {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		for (int i = 0; i < tamanho; i++) {
			sb.append(vetor[i].id);
			if (i != tamanho - 1) sb.append(","); 
		}

		sb.append("]");
		return sb.toString();
	}

	//retorna vetor de formato [a, b] sendo 'a' o resultado da alocação (0 ou 1, fracasso ou sucesso)
	//e 'b' o índice do assento alocado, em caso de sucesso
	public int[] alocaAssentoLivre(int threadID) {
		int[] aux = new int[2];
		if (assentosLivres.size() == 0) { //se não houver assentos livres, retorna fracasso
			aux[0] = 0; aux[1] = 0;
			return aux; }

		//se houver assentos livres, retorna o primeiro, e retira ele da lista de assentos livres
		int assentoSelecionado = assentosLivres.get(0);
		vetor[assentoSelecionado].aloca(threadID);
		assentosLivres.remove(0);

		aux[0] = 1;
		aux[1] = assentoSelecionado;
		return aux;
	}

	public int alocaAssentoDado(int threadID, int assentoId) {
		//retorna fracasso se assento tem indice invalido ou se não é possivel aloca-lo
		if (assentoId < 0 || assentoId > vetor.length) return 0;
		if (!vetor[assentoId].aloca(threadID)) return 0;

		//caso contrario, remove ele da lista de assentos livres: ele foi alocado
		assentosLivres.remove((Integer) assentoId);

		return 1;
	}

	public int liberaAssento(int threadID, int assentoId) {
		//retorna fracasso se assento tem indice invalido ou se não é possivel desaloca-lo
		if (assentoId < 0 || assentoId > vetor.length) return 0;
		if (!vetor[assentoId].desaloca(threadID)) return 0;

		//caso contrario, adiciona ele na lista de assentos livres: ele foi desalocado
		assentosLivres.add(assentoId);

		return 1;
	}

	//métodos básicos de leitor/escritor
	public synchronized void entraLeitura() {
		try {
			while (escritor || escritoresEspera > 0)
				this.wait();
			leitores++;
		} catch (InterruptedException e) {}
	}

	public synchronized void saiLeitura() {
		leitores--;
		if (leitores == 0) this.notify();
	}

	public synchronized void entraEscrita() {
		try {
			escritoresEspera++;
			while (leitores > 0 || escritor)
				this.wait();
			escritoresEspera--;
			escritor = true;
		} catch (InterruptedException e) {}
	}

	public synchronized void saiEscrita() {
		escritor = false;		
		this.notifyAll();
	}
}

//--------------------------------------------------------
//classe monitor para os produtores (classes usuário) e o consumidor (classe consumidor)
//código básico de inserção/remoçao cíclicas visto em sala
class Buffer {
	int in,
		out,
		tamanho,
		contagem;

	String[] buffer;
	boolean encerrar;

	public Buffer(int tamanho) {
		this.tamanho = tamanho;
		this.buffer = new String[tamanho];
		this.in = 0;
		this.out = 0;
		this.encerrar = false;

		for (int i = 0; i < tamanho; i++)
			this.buffer[i] = "vazio";
	}

	public synchronized void adicionaElemento(String elemento) {
		try {
			while (contagem == tamanho) {
				System.out.println("[B]: Tentando adicionar elemento '" + elemento + "' ao buffer. Buffer cheio. Em espera.");
				this.wait();
			}

			buffer[in] = elemento;
			in = (in + 1) % tamanho;
			contagem++;
			this.notifyAll();
		} catch (InterruptedException e) { }
	}

	public synchronized String removeElemento() {
		try {
			while (contagem == 0) {
				//ver método 'encerrar()'
				if (encerrar) {
					System.out.println("[B]: Usuarios encerraram requisicoes. Buffer vazio. Encerrando buffer.");
					return null;
				}

				System.out.println("[B]: Tentando remover elemento do buffer. Buffer vazio. Em espera.");
				this.wait();
			}

			String aux = buffer[out];
			out = (out + 1) % tamanho;
			contagem--;
			this.notifyAll();
			return aux;
		} catch (InterruptedException e) { return null; }
	}

	//se o buffer estiver vazio, pode haver a chance de nunca mais ser preenchido
	//isso acontece se as threads produtoras tiverem finalizado. quando isso ocorrer,
	//o buffer é sinalizado por meio do método encerrar()
	public synchronized void encerrar() {
		encerrar = true;
		this.notify(); //desbloqueia o consumidor
	}
}

//--------------------------------------------------------
//consome elementos do buffer e os adiciona em um arquivo de saída
class Consumidor extends Thread {
	int id;
	Buffer buffer;
	boolean encerrar;

	public Consumidor(int id, Buffer buffer) {
		this.id = id;
		this.buffer = buffer;
		this.encerrar = false;
	}

	public void run() {
		try {
			PrintWriter writer = new PrintWriter("log.txt", "UTF-8");
			String aux = "=== INICIO DO LOG ===";
			writer.println(aux);

			//quando as threads produtoras tiverem parado de produzir, o consumidor
			//também deve parar de consumir, por isso a condição do laço
			while (!encerrar) {
				aux = buffer.removeElemento();
				if (aux != null) writer.println(aux);
			}

			writer.println("=== FIM DO LOG ===");
			writer.close();
		} catch (FileNotFoundException | UnsupportedEncodingException e) { }
	}

	//sinaliza para o buffer encerrar e encerra
	public void encerrar() {
		encerrar = true;
		buffer.encerrar();
	}
}

//--------------------------------------------------------
//classes produtoras do log que fazem requisições ao monitor 'assentos'
class Usuario extends Thread {
	int id;
	UsuarioCreator usuarioCreator;
	Assentos assentos;
	Buffer buffer;

	public Usuario(int id, Assentos assentos, Buffer buffer, UsuarioCreator usuarioCreator) {
		this.id = id;
		this.assentos = assentos;
		this.buffer = buffer;
		this.usuarioCreator = usuarioCreator;
	}

	static final int numeroMaximoIteracoes = 5;
	public void run() {
		Random random = new Random();
		int assentoAlocado = -1;

		//numero aleatorio de iteracoes
		for (int i = random.nextInt(numeroMaximoIteracoes); i > 0; i--) {
			//joga uma moeda para ver se vai alocar um assento livre, um assento especifico ou fazer nada
			int chance = random.nextInt(3);
			switch (chance) {
				case 0:
					assentoAlocado = requisitaAssentoLivre();
					break;
				case 1:
					int assentoTentado = random.nextInt(assentos.getTamanho());
					if (requisitaAssentoDado(assentoTentado))
						assentoAlocado = assentoTentado;
					break;
				default:
					break;
			}

			//processamento bobo (pensa um pouco antes de desalocar)
			int boba1 = 0, boba2 = 0;
			for (; boba1 < 10000; boba1++) boba2 += boba1;

			//joga uma moeda pra decidir se vai requisitar visualizações ou não
			chance = random.nextInt(2);
			switch (chance) {
				case 0: //caso decida requisitar visualizações, joga um dado pra ver quantas vezes faz isso
					for (int j = random.nextInt(numeroMaximoIteracoes); j > 0; j--)
						requisitaVisualizacao();
					break;
				case 1: default:
					break;
			}
			
			//caso tenha alocado algum assento, desaloca ele. caso nao tenha alocado
			//nenhum assento, tenta desalocar um aleatorio
			if (assentoAlocado != -1)
				requisitaLiberacaoAssento(assentoAlocado);
			else
				requisitaLiberacaoAssento(random.nextInt(assentos.getTamanho()));
		}
		usuarioCreator.criarNovaThread(id);
	}

	//método de leitura do mapa de assentos
	private void requisitaVisualizacao() {
		System.out.println("[1] Usuario #" + id + " requisitando visualizacao de assentos.");

		assentos.entraLeitura();
		//
		String mapa = assentos.visualizaAssentos();
		buffer.adicionaElemento("1," + id + "," + mapa);
		//
		assentos.saiLeitura();
	}

	//método de escrita no mapa de assentos
	private int requisitaAssentoLivre() {
		System.out.println("[2] Usuario #" + id + " requisitando assento livre.");

		assentos.entraEscrita();
		//
		int[] resultado = assentos.alocaAssentoLivre(id);
		String mapa = assentos.visualizaAssentos();
		if (resultado[0] == 0) {
			System.out.println("[2] Usuario #" + id + " mal-sucedido em alocar um assento livre. Todos os assentos estao cheios");
			buffer.adicionaElemento("2," + id + "," + -1 + "," + mapa);
		} else {
			System.out.println("[2] Usuario #" + id + " bem-sucedido em alocar um assento livre. O assento alocado foi '" + resultado[1] + "'.");
			buffer.adicionaElemento("2," + id + "," + (resultado[1] + 1) + "," + mapa);
		}
		//
		assentos.saiEscrita();

		return resultado[1];
	}

	//método de escrita no mapa de assentos
	//retorna true se conseguiu alocar o assento, false caso contrário
	private boolean requisitaAssentoDado(int assentoId) {
		System.out.println("[3] Usuario #" + id + " requisitando assento dado '" + assentoId + "'.");
		boolean sucesso;

		assentos.entraEscrita();
		//
		int resultado = assentos.alocaAssentoDado(id, assentoId);
		String mapa = assentos.visualizaAssentos();
		if (resultado == 0) {
			System.out.println("[3] Usuario #" + id + " mal-sucedido em alocar o assento dado '" + assentoId + "'.");
			sucesso = false;
		} else {
			System.out.println("[3] Usuario #" + id + " bem-sucedido em alocar o assento dado '" + assentoId + "'.");
			sucesso = true;
		}
		buffer.adicionaElemento("3," + id + "," + (assentoId + 1) + "," + mapa);
		//
		assentos.saiEscrita();

		return sucesso;
	}

	//método de escrita no mapa de assentos
	private void requisitaLiberacaoAssento(int assentoId) {
		System.out.println("[4] Usuario #" + id + " requisitando liberacao do assento dado '" + assentoId + "'.");
		
		assentos.entraEscrita();
		//
		int resultado = assentos.liberaAssento(id, assentoId);
		String mapa = assentos.visualizaAssentos();
		if (resultado == 0) {
			System.out.println("[4] Usuario #" + id + " mal-sucedido em desalocar o assento dado '" + assentoId + "'."); 
			buffer.adicionaElemento("4," + id + "," + (assentoId + 1) + "," + mapa);
		} else {
			System.out.println("[4] Usuario #" + id + " bem-sucedido em desalocar o assento dado '" + assentoId + "'.");
			buffer.adicionaElemento("4," + id + "," + (assentoId + 1) + "," + mapa);
		}
		//
		assentos.saiEscrita();
	}
}

//--------------------------------------------------------
//classe feita para criação dinâmica de threads usuário
//toda thread usuário é criada por UsuarioCreator
class UsuarioCreator {
	Assentos assentos;
	Buffer buffer;
	Consumidor consumidor;
	int limite,
		contadorThreads = 0,
		threadsAtivas = 0,
		quantInicial;

	public UsuarioCreator(int quantInicial, int limite, Assentos assentos, Buffer buffer, Consumidor consumidor) {
		this.quantInicial = quantInicial;
		this.limite = limite;		
		this.assentos = assentos;
		this.buffer = buffer;
		this.consumidor = consumidor;
	}

	//criaçao das threads iniciais, que gerarão outras threads quando morrerem
	public void iniciar() {
		for (int i = 0; i < quantInicial; i++) {
			Usuario usuario = new Usuario(contadorThreads + 1, assentos, buffer, this);
			usuario.start();
			contadorThreads++;
			threadsAtivas++;
		}
	}

	//método chamado por threads quando elas estiverem encerrando
	//para que novas threads sejam criadas em seus lugares
	public synchronized void criarNovaThread(int threadRequisitanteID) {
		threadsAtivas--;
		//o numero de threads ja extrapolou o limite, nenhuma thread pode ser criada a mais
		if (contadorThreads > limite - 1) { 
			System.out.println("Thread " + contadorThreads + " extrapolando o limite.");
			if (threadsAtivas == 0) { //ultima thread em execução envia sinal para que o consumidor encerre
				System.out.println("Thread " + threadRequisitanteID + " encerrou, e era a última thread ativa. Encerrando aplicação.");
				consumidor.encerrar(); 
			}
			return;
		}
		
		System.out.println("Thread " + threadRequisitanteID + " pediu para criar nova thread de id " + contadorThreads);
		Usuario usuario = new Usuario(contadorThreads + 1, assentos, buffer, this);
		usuario.start();
		contadorThreads++;
		threadsAtivas++;
	}
}

//--------------------------------------------------------
//Classe principal
//cria as estruturas e as passa como referencia umas pras outras
//pede para UsuarioCreator criar um certo numero de usuarios
class Main {
  static final int TotalUsuarios = 1000;
  static final int UsuariosIniciais = 5;
  static final int NumeroAssentos = 5;

  public static void main(String[] args) {
    int i, delay = 1000;
	Buffer buffer = new Buffer(UsuariosIniciais);
    Assentos assentos = new Assentos(NumeroAssentos);

	Consumidor consumidor = new Consumidor(0, buffer);
	consumidor.start();

	UsuarioCreator usuarioCreator = new UsuarioCreator(UsuariosIniciais, TotalUsuarios, assentos, buffer, consumidor);
	usuarioCreator.iniciar();
  }
}