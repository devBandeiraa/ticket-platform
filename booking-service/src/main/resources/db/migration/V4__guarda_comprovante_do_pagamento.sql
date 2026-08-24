-- Comprovante devolvido pelo provedor de pagamento.
--
-- Guardado por uma razao pratica: e o unico identificador que permite estornar a cobranca depois.
-- Sem ele, uma reserva confirmada por engano so poderia ser corrigida procurando a cobranca no
-- painel do provedor, na mao, pelo valor e pelo horario.
--
-- Nulo para as reservas ja existentes e para qualquer reserva que ainda nao foi paga. Nao ha
-- valor de preenchimento honesto: cobranca que nao aconteceu nao tem comprovante, e inventar um
-- placeholder faria uma consulta por "reservas pagas sem comprovante" devolver nada justamente
-- quando ela precisaria acusar um problema.
ALTER TABLE bookings
    ADD COLUMN payment_authorization VARCHAR(40);

-- So faz sentido em reserva confirmada, e toda reserva confirmada daqui em diante tera o seu.
-- A constraint permite ambos os lados nulos para nao invalidar as linhas antigas, que foram
-- confirmadas quando o pagamento ainda era apenas uma troca de status.
COMMENT ON COLUMN bookings.payment_authorization IS
    'Comprovante do provedor de pagamento; necessario para estorno';
