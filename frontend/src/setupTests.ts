/*
  Matchers de DOM para os testes.

  `@testing-library/jest-dom` ja estava no package.json, mas nao era importado em lugar
  nenhum — dependencia declarada e nao usada. Registrada aqui, ela passa a valer para todos os
  arquivos de teste, e asserções como `toBeInTheDocument` ou `toHaveAccessibleName` ficam
  disponiveis sem repetir o import.

  `toHaveAccessibleName` e a razao concreta: verificar acessibilidade sem ela exigiria comparar
  atributos `aria-*` na mao, que testa a implementacao e nao o que o leitor de tela anuncia.
*/
import '@testing-library/jest-dom/vitest'
