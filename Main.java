
/* Disciplina: Computacao Concorrente */
/* Prof.: Silvana Rossetto */
/* Trabalho 2 */
/* Estudantes: Vinícius Garcia (115.039.031)
/*             Rafael Katopodis (115.021.282)
/* -------------------------------------------------------------------*/

import java.util.*;
import java.io.*;

class Assentos {
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

	Assento[] vetor;
	int tamanho,
		leitores,
		escritoresEspera;
	boolean escritor;
	List<Integer> assentosLivres;

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

	public int[] alocaAssentoLivre(int threadID) {
		//aux é um vetor de formato [a, b] sendo 'a' o resultado da alocação (0 ou 1, fracasso ou sucesso)
		//e 'b' o índice do assento alocado, em caso de sucesso
		int[] aux = new int[2];
		if (assentosLivres.size() == 0) {
			aux[0] = 0; aux[1] = 0;
			return aux; }

		int assentoSelecionado = assentosLivres.get(0);
		vetor[assentoSelecionado].aloca(threadID);
		assentosLivres.remove(0);

		aux[0] = 1;
		aux[1] = assentoSelecionado;
		return aux;
	}

	public int alocaAssentoDado(int threadID, int assentoId) {
		if (assentoId < 0 || assentoId > vetor.length) return 0;
		if (!vetor[assentoId].aloca(threadID)) return 0;

		assentosLivres.remove((Integer) assentoId);

		return 1;
	}

	public int liberaAssento(int threadID, int assentoId) {
		if (assentoId < 0 || assentoId > vetor.length) return 0;
		if (!vetor[assentoId].desaloca(threadID)) return 0;

		assentosLivres.add(assentoId);

		return 1;
	}

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

	public synchronized void encerrar() {
		encerrar = true;
		this.notify(); //desbloqueia o consumidor
	}
}

//escreve as coisas no arquivo de saída
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

			while (!encerrar) {
				aux = buffer.removeElemento();
				if (aux != null) writer.println(aux);
			}

			writer.println("=== FIM DO LOG ===");
			writer.close();
		} catch (FileNotFoundException | UnsupportedEncodingException e) { }
	}

	public void encerrar() {
		encerrar = true;
		buffer.encerrar();
	}
}

//fazem requisições ao sistema (monitor)
class Usuario extends Thread {
	int id;
	ThreadCreator threadCreator;
	Assentos assentos;
	Buffer buffer;

	public Usuario(int id, Assentos assentos, Buffer buffer, ThreadCreator threadCreator) {
		this.id = id;
		this.assentos = assentos;
		this.buffer = buffer;
		this.threadCreator = threadCreator;
	}

	public void run() {
		Random random = new Random();
		int assentoAlocado = -1; //assentoAlocado como -1 possui o significado de nenhum assento alocado

		int chance = random.nextInt(2);
		switch (chance) {
			case 0:
				assentoAlocado = requisitaAssentoLivre();
			case 1: default:
				int assentoTentado = random.nextInt(assentos.getTamanho());
				if (requisitaAssentoDado(assentoTentado))
					assentoAlocado = assentoTentado;
		}

		//processamento bobo (pensa um pouco antes de desalocar)
		int boba1 = 0, boba2 = 0;
		for (; boba1 < 1000; boba1++) boba2 += boba1;

		//tem chance de visualizar o mapa de assentos
		chance = random.nextInt(2);
		switch (chance) {
			case 0:
				requisitaVisualizacao();
			default:
		}
		
		requisitaLiberacaoAssento(assentoAlocado);
		threadCreator.criarNovaThread(id);
	}

	private void requisitaVisualizacao() {
		System.out.println("[1] Usuario #" + id + " requisitando visualizacao de assentos.");

		assentos.entraLeitura();
		//
		String mapa = assentos.visualizaAssentos();
		buffer.adicionaElemento("1," + id + "," + mapa);
		//
		assentos.saiLeitura();
	}

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

	private boolean requisitaAssentoDado(int assentoId) {
		System.out.println("[3] Usuario #" + id + " requisitando assento dado '" + assentoId + "'.");
		boolean sucesso;

		assentos.entraEscrita();
		//
		int resultado = assentos.alocaAssentoDado(id, assentoId);
		String mapa = assentos.visualizaAssentos();
		buffer.adicionaElemento("3," + id + "," + assentoId + "," + mapa);
		if (resultado == 0) {
			System.out.println("[3] Usuario #" + id + " mal-sucedido em alocar o assento dado '" + assentoId + "'.");
			buffer.adicionaElemento("3," + id + "," + -1 + "," + mapa);
			sucesso = false;
		} else {
			System.out.println("[3] Usuario #" + id + " bem-sucedido em alocar o assento dado '" + assentoId + "'.");
			buffer.adicionaElemento("3," + id + "," + (assentoId + 1) + "," + mapa);
			sucesso = true;
		}
		//
		assentos.saiEscrita();

		return sucesso;
	}

	private void requisitaLiberacaoAssento(int assentoId) {
		System.out.println("[4] Usuario #" + id + " requisitando liberacao do assento dado '" + assentoId + "'.");
		
		assentos.entraEscrita();
		//
		int resultado = assentos.liberaAssento(id, assentoId);
		String mapa = assentos.visualizaAssentos();
		if (resultado == 0) {
			System.out.println("[4] Usuario #" + id + " mal-sucedido em desalocar o assento dado '" + assentoId + "'."); 
			buffer.adicionaElemento("4," + id + "," + -1 + "," + mapa);
		} else {
			System.out.println("[4] Usuario #" + id + " bem-sucedido em desalocar o assento dado '" + assentoId + "'.");
			buffer.adicionaElemento("4," + id + "," + (assentoId + 1) + "," + mapa);
		}
		//
		assentos.saiEscrita();
	}
}

class ThreadCreator {
	Assentos assentos;
	Buffer buffer;
	Consumidor consumidor;
	int limite = 40,
		contadorThreads = 1,
		quantInicial;

	public ThreadCreator(int quantInicial, int limite, Assentos assentos, Buffer buffer, Consumidor consumidor) {
		this.quantInicial = quantInicial;
		this.limite = limite;		
		this.assentos = assentos;
		this.buffer = buffer;
		this.consumidor = consumidor;
	}

	public void iniciar() {
		for (int i = 0; i < quantInicial; i++)
			criarNovaThread(-1);
	}

	public synchronized void criarNovaThread(int calleeId) {
		if (calleeId == limite) { 
			System.out.println("Thread " + calleeId + " encerrou. Encerrando programa...");
			consumidor.encerrar(); 
			return; }
		if (contadorThreads > limite) return;
		
		System.out.println("Thread " + calleeId + " pediu para criar nova thread de id " + contadorThreads);
		Usuario usuario = new Usuario(contadorThreads, assentos, buffer, this);
		usuario.start();
		contadorThreads++;
	}
}

//--------------------------------------------------------
// Classe principal
class Main {
  static final int TotalUsuarios = 5000;
  static final int UsuariosIniciais = 1000;
  static final int NumeroAssentos = 5;

  public static void main(String[] args) {
    int i, delay = 1000;
	Buffer buffer = new Buffer(TotalUsuarios);
    Assentos assentos = new Assentos(NumeroAssentos);

	Consumidor consumidor = new Consumidor(0, buffer);
	consumidor.start();

	ThreadCreator threadCreator = new ThreadCreator(UsuariosIniciais, TotalUsuarios, assentos, buffer, consumidor);
	threadCreator.iniciar();
  }
}