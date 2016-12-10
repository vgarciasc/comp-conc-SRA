
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
		assentosLivres,
		leitores,
		escritores;

	public Assentos(int tamanho) {
		this.tamanho = tamanho;
		this.assentosLivres = tamanho;
		leitores = escritores = 0;

		this.vetor = new Assento[tamanho];
		for (int i = 0; i < tamanho; i++)
			this.vetor[i] = new Assento();
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

	public int[] alocaAssentoLivre(int id) {
		//aux é um vetor de formato [a, b] sendo 'a' o resultado da alocação (0 ou 1, fracasso ou sucesso)
		//e 'b' o índice do assento alocado, em caso de sucesso
		int[] aux = new int[2];
		if (assentosLivres == 0) { aux[0] = 0; aux[1] = 0; return aux; }

		Random random = new Random();
		int possivelAssento;

		//procura aleatoriamente um assento livre
		do { possivelAssento = random.nextInt(tamanho);
		} while (!vetor[possivelAssento].aloca(id));

		assentosLivres--;

		aux[0] = 1;
		aux[1] = possivelAssento;
		return aux;
	}

	public int alocaAssentoDado(int id, int assentoId) {
		if (assentoId < 0 || assentoId > vetor.length) return 0;
		if (!vetor[assentoId].aloca(id)) return 0;

		assentosLivres--;

		return 1;
	}

	public int liberaAssento(int id, int assentoId) {
		if (assentoId < 0 || assentoId > vetor.length) return 0;
		if (!vetor[assentoId].desaloca(id)) return 0;

		assentosLivres++;

		return 1;
	}

	public synchronized void entraLeitura() {
		try {
			while (escritores > 0)
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
			while (leitores > 0 || escritores > 0)
				this.wait();
			escritores++;
		} catch (InterruptedException e) {}
	}

	public synchronized void saiEscrita() {
		escritores--;		
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
		int aux;

		int chance = random.nextInt(2);
		if (chance == 0) {
			//tenta alocar um assento livre qualquer
			assentoAlocado = requisitaAssentoLivre();
		} else if (chance == 1) {
			//tenta alocar um assento específico selecionado aleatoriamente
			//caso seja bem-sucedido, este é o assento alocado
			aux = random.nextInt(assentos.getTamanho());
			if (requisitaAssentoDado(aux))
				assentoAlocado = aux;
		}

		//processamento bobo (pensa um pouco antes de desalocar)
		int boba1 = 0, boba2 = 0;
		for (; boba1 < 1000; boba1++) boba2 += boba1;

		//tem chance de visualizar o mapa de assentos
		chance = random.nextInt(2);
		if (chance == 0)
			requisitaVisualizacao();
		
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
			buffer.adicionaElemento("2," + id + "," + -1 + "," + mapa);
			System.out.println("[2] Usuario #" + id + " mal-sucedido em alocar um assento livre. Todos os assentos estao cheios");
		} else {
			buffer.adicionaElemento("2," + id + "," + resultado[1] + "," + mapa);
			System.out.println("[2] Usuario #" + id + " bem-sucedido em alocar um assento livre. O assento alocado foi '" + resultado[1] + "'.");
		}
		//
		assentos.saiEscrita();

		return resultado[1];
	}

	private boolean requisitaAssentoDado(int assentoId) {
		System.out.println("[3] Usuario #" + id + " requisitando assento dado '" + assentoId + "'.");
		
		assentos.entraEscrita();
		//
		int resultado = assentos.alocaAssentoDado(id, assentoId);
		String mapa = assentos.visualizaAssentos();
		buffer.adicionaElemento("3," + id + "," + assentoId + "," + mapa);
		if (resultado == 0) {
			System.out.println("[3] Usuario #" + id + " mal-sucedido em alocar o assento dado '" + assentoId + "'.");
			buffer.adicionaElemento("3," + id + "," + -1 + "," + mapa);
			return false;
		} else {
			System.out.println("[3] Usuario #" + id + " bem-sucedido em alocar o assento dado '" + assentoId + "'.");
			buffer.adicionaElemento("3," + id + "," + assentoId + "," + mapa);
			return true;
		}
		//
		assentos.saiEscrita();


		return false;
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
			buffer.adicionaElemento("4," + id + "," + assentoId + "," + mapa);
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
  static final int TotalUsuarios = 5;
  static final int UsuariosIniciais = 5;
  static final int A = 5;

  public static void main(String[] args) {
    int i, delay = 1000;
	Buffer buffer = new Buffer(U);
    Usuario[] usuarios = new Usuario[U];
    Assentos assentos = new Assentos(A);

	Consumidor consumidor = new Consumidor(0, buffer);
	consumidor.start();

	ThreadCreator threadCreator = new ThreadCreator(UsuariosIniciais, TotalUsuarios, assentos, buffer, consumidor);
	threadCreator.iniciar();

    /*for (i = 0; i < U; i++) {
       usuarios[i] = new Usuario(i + 1, delay, assentos, buffer, threadCreator);
       usuarios[i].start(); 
    }

    try {
    	for (i = 0; i < U; i++)
       		usuarios[i].join(); 
    } catch (InterruptedException e) { } 

   	consumidor.encerrar();
   	System.out.println(">> Fim");*/
  }
}