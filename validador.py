#!/usr/bin/env python3

import sys, ast

def validaLog(nomeArquivo):
	'''Itera pelo arquivo de log especificado, verificando se as operações realizadas são válidas'''
	try:
		log = open(nomeArquivo, 'r')
	except FileNotFoundError:
		print('Erro: Arquivo não encontrado ( ' + nomeArquivo + ' )')
		sys.exit(1)
		
	print('Arquivo aberto com sucesso')

	# Salta cabeçalho
	log.readline()

	# Inicializa mapaAssentosAnterior
	mapaAssentosAnterior = inicializaMapaAssentos(primeiraLinha(log))
	print('mapaAssentosAnterior: ' + str(mapaAssentosAnterior))
	
	n_linha = 0
	# Itera pelo log
	for linha in log:
		linha = linha.strip('\n')
		n_linha += 1

		if linha == '=== FIM DO LOG ===':
			break
		
		# Avalia linha corrente
		operacao, mapaAssentosProximo = avaliaLinha(linha)

		# Compara mapa anterior com próximo mapa

		# Verifica se as mudanças foram consistentes com a operação realizada
		## Se foram, segue em frente
		## se não foram, imprime mensagem de erro apropriada 
		print(linha)


		
	log.close()

def primeiraLinha(arquivo):
	pos = arquivo.tell()
	linha = arquivo.readline()
	arquivo.seek(pos)

	return linha

def avaliaLinha(linha):
	'''Avalia uma linha do log, quebrando-a em seu mapa de assentos e operação realizada'''
	string_operacao, string_mapa = linha.split('[')

	operacao = ast.literal_eval('[' + string_operacao + ']')
	mapa = ast.literal_eval('[' + string_mapa)

	return (operacao, mapa)

def geraLinha(operacao, mapa):
	string_operacao = str(operacao).strip('[').strip(']') + ','

	return string_operacao + str(mapa)

def inicializaMapaAssentos(primeiraLinha):
	'''Gera o mapa de assentos inicial, tomando como referência o mapa na primeira linha do log'''
	_, mapaReferencia = avaliaLinha(primeiraLinha)

	return [0]*len(mapaReferencia)

def comparaMapas(mapaAnterior, mapaProximo, operacao, n_linha):

	# 1º: Verifica se mapas tem mesmo tamanho
	if len(mapaAnterior) != len(mapaProximo):
		print('Erro: Aleração do número de assentos (linha: ' + n_linha + ')')
		print(geraLinha(operacao, mapaProximo))
		sys.exit(1)

	#


if __name__ == '__main__':
	if(len(sys.argv) != 2):
		print('Uso: validator.py <arquivo de log>')
		sys.exit(1)

	validaLog(sys.argv[1])