#!/usr/bin/env python3

import sys, ast, os

# TODO: Se um erro for encontrado no laço, o arquivo não será fechado
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

	mapaAssentosAnterior = inicializaMapaAssentos(primeiraLinha(log))
	
	n_linha = 0
	# Itera pelo log
	for linha in log:
		linha = linha.strip('\n')
		n_linha += 1

		if linha == '=== FIM DO LOG ===':
			print('Nenhum erro encontrado')
			break
		
		# Avalia linha corrente
		operacao, mapaAssentosProximo = avaliaLinha(linha)

		validaLinha(n_linha, mapaAssentosAnterior, mapaAssentosProximo, operacao)

		
		mapaAssentosAnterior = mapaAssentosProximo


	log.close()

def primeiraLinha(arquivo):
	'''Retorna a primeira linha do arquivo sem mover o file pointer'''
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

# def geraLinha(operacao, mapa):
# 	'''Reconstrói linha do log a partir de descrição de operação e um mapa'''
# 	string_operacao = str(operacao).strip('[').strip(']') + ','

# 	return string_operacao + str(mapa)

def inicializaMapaAssentos(primeiraLinha):
	'''Gera o mapa de assentos inicial, tomando como referência o mapa na primeira linha do log'''
	_, mapaReferencia = avaliaLinha(primeiraLinha)

	return [0]*len(mapaReferencia)

def validaLinha(linha, mapaAntes, mapaDepois, operacao):
	if len(operacao) == 3:
			codigoOperacao, tid, indiceAssento = operacao # indiceAssento inicia contagem a partir de 1
	else:
		codigoOperacao, tid = operacao

	if codigoOperacao == 1: # Visualiza Assentos
		if mapaDepois != mapaAntes:
			print('Erro: Operação visualizaAssentos alterando assentos (linha ' + str(linha) + ')')
			imprimeDetalhesDeErro(tid, mapaAntes, mapaDepois)
			sys.exit(1)
	elif codigoOperacao == 2: # Aloca Assento Livre
		if indiceAssento == -1:
			if mapaDepois != mapaAntes:
				print('Erro: Operação alocaAssentoLivre altera mapa sem assentos livres (linha ' + str(linha) + ')')
				imprimeDetalhesDeErro(tid, mapaAntes, mapaDepois, indiceAssento)
				sys.exit(1)
		elif not 1 <= indiceAssento <= len(mapaAssentosProximo):
			print('Erro: Operação alocaAssentoLivre tenta alocar assento inexistente (linha ' + str(linha) + ')')
			imprimeDetalhesDeErro(tid, mapaAntes, mapaDepois, indiceAssento)
			sys.exit(1)
		elif mapaAntes[indiceAssento - 1] != 0:
			print('Erro: Operação alocaAssentoLivre toma um assento ocupado (linha ' + str(linha) + ')')
			imprimeDetalhesDeErro(tid, mapaAntes, mapaDepois, indiceAssento)
			sys.exit(1)
		elif mapaDepois[indiceAssento - 1] != tid:
			print('Erro: Operação alocaAssentoLivre não registra id de sua thread no assento alocado (linha ' + str(_linha) + ')')
			imprimeDetalhesDeErro(tid, mapaAntes, mapaDepois, indiceAssento)
			sys.exit(1)
		# Verificar aqui se demais assentos não foram alterados
		if demaisAssentosInalterados(mapaAntes, mapaDepois, indiceAssento) == False:
			print('Erro: Operação alocaAssentoLivre altera mais de um assento (linha ' + str(linha) + ')')
			imprimeDetalhesDeErro(tid, mapaAntes, mapaDepois, indiceAssento)
			sys.exit(1)
	elif codigoOperacao == 3: # Aloca Assento Dado
		if not 1 <= indiceAssento <= len(mapaAssentosProximo):
			print('Erro: Operação alocaAssentoDado tenta alocar assento inexistente (linha ' + str(linha) + ')')
			imprimeDetalhesDeErro(tid, mapaAntes, mapaDepois, indiceAssento)
			sys.exit(1)
		elif mapaAntes[indiceAssento - 1] != 0:
			if mapaDepois != mapaAntes:
				print('Erro: Operação alocaAssentoDado altera mapa de assentos sem assento especificado estar livre')
				imprimeDetalhesDeErro(tid, mapaAntes, mapaDepois, indiceAssento)
				sys.exit(1)
		elif mapaDepois[indiceAssento - 1] != tid:
			print('Erro: Operação alocaAssentoDado não registra id de sua thread no assento alocado (linha ' + str(linha) +')')
			imprimeDetalhesDeErro(tid, mapaAntes, mapaDepois, indiceAssento)
			sys.exit(1)
		# Verificar aqui se demais assentos não foram alterados
		if demaisAssentosInalterados(mapaAntes, mapaDepois, indiceAssento) == False:
			print('Erro: Operação alocaAssentoDado altera mais de um assento (linha: ' + str(linha) + ')')
			imprimeDetalhesDeErro(tid, mapaAntes, mapaDepois, indiceAssento)
			sys.exit(1)
	elif codigoOperacao == 4: # Libera Assento
		if not 1 <= indiceAssento <= len(mapaAssentosProximo):
			print('Erro: Operação liberaAssento tenta liberar assento inexistente (linha ' + str(linha) + ')')
			imprimeDetalhesDeErro(tid, mapaAntes, mapaDepois, indiceAssento)
			sys.exit(1)
		elif mapaAntes[indiceAssento - 1] != tid:
			if mapaDepois != mapaAntes:
				print('Erro: Operação liberaAssento altera mapa de assentos ao tentar liberar assento não alocado previamente (linha ' + str(linha) + ')')
				imprimeDetalhesDeErro(tid, mapaAntes, mapaDepois, indiceAssento)
				sys.exit(1)
		elif mapaDepois[indiceAssento - 1] != 0:
			print('Erro: Operação liberaAssento não remove id de sua thread de assento previamente alocado (linha ' + str(linha) + ')')
			imprimeDetalhesDeErro(tid, mapaAntes, mapaDepois, indiceAssento)
			sys.exit(1)
		# Verificar aqui se demais assentos não foram alterados
		if demaisAssentosInalterados(mapaAntes, mapaDepois, indiceAssento) == False:
			print('Erro: operação liberaAssento altera mais de um assento (linha ' +  str(linha) + ')')
			imprimeDetalhesDeErro(tid, mapaAntes, mapaDepois, indiceAssento)
			sys.exit(1)

def demaisAssentosInalterados(mapaAntes, mapaDepois, indice):
	'''Recebe duas listas de mesmo tamanho e verifica se todos os elementos 
	correspondentes são iguais, com exceção dos elementos na posição indice em abas as listas'''

	return mapaAntes[:indice - 1] == mapaDepois[:indice - 1] and mapaAntes[indice:] == mapaDepois[indice:]

def imprimeDetalhesDeErro(tid, mapaAntes, mapaDepois, indice=None):
	separador = '-' * os.get_terminal_size().columns
	print(separador)
	print('Detalhes:')
	print('thread: ' + str(tid))
	if indice != None: print('Índice assento: ' + str(indice))

	print('\nMapa de assentos antes:')
	print(str(mapaAntes))
	print('\nMapa de assentos depois:')
	print(str(mapaDepois))



if __name__ == '__main__':
	if(len(sys.argv) != 2):
		print('Uso: validator.py <arquivo de log>')
		sys.exit(1)

	validaLog(sys.argv[1])