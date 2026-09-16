import type { CategoriaDoEvento } from '../api/tipos'

/**
 * Rotulos das categorias.
 *
 * <p>O valor e o mesmo enum do backend; o texto e so apresentacao. Ficam num `Record` completo,
 * e nao numa lista derivada do tipo, porque o TypeScript apaga o tipo na compilacao — nao ha
 * como percorrer `CategoriaDoEvento` em tempo de execucao.
 *
 * <p>O `Record` garante o que importa: acrescentar uma categoria no backend e esquecer o rotulo
 * aqui vira erro de compilacao, e nao um seletor incompleto que ninguem nota.
 *
 * <p>Moram neste modulo, e nao dentro de uma tela, porque tres as usam: o formulario
 * administrativo, os filtros rapidos da home e a etiqueta do cartao. Duplicadas, bastaria
 * traduzir uma delas diferente para a mesma categoria aparecer com dois nomes.
 */
export const ROTULOS_DE_CATEGORIA: Record<CategoriaDoEvento, string> = {
  SHOWS: 'Shows',
  FESTIVAIS: 'Festivais',
  ESPORTES: 'Esportes',
  TECNOLOGIA: 'Tecnologia',
  TEATRO: 'Teatro',
  FESTAS: 'Festas',
}

/** Na ordem em que os filtros rapidos as exibem. */
export const CATEGORIAS = Object.keys(ROTULOS_DE_CATEGORIA) as CategoriaDoEvento[]
