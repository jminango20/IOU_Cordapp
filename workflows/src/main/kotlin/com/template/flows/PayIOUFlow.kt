package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.template.contracts.IOUContract
import com.template.states.IOUState
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.util.*

object PayIOUFlow {

    @InitiatingFlow
    @StartableByRPC
    class Initiator(val stateId: UUID, val payment: Int) : FlowLogic<SignedTransaction>(){

        @Suspendable
        override fun call(): SignedTransaction {
            //para se fazer queries, é necessário construir os critérios de seleção, utilizamos então as QueryCriterias.
            //Conseguimos consultar LinearStates diretamente por seu UUID.
            val criteria = QueryCriteria.LinearStateQueryCriteria(uuid = listOf(stateId))
            val oldState = serviceHub.vaultService.queryBy<IOUState>(criteria).states.single()

            requireThat {
                "Apenas o Borrower pode pagar o IOU" using (ourIdentity == oldState.state.data.borrower)
            }

            val notary = oldState.state.notary

            //O Initiator deve construir o novo State com base no State antigo
            //A função copy, permite uma cópia de uma data class modificando apenas os valores que você deseja
            val newSate = oldState.state.data.copy(payment = payment)

            //O Initiator deve criar uma transação com o State antigo como input e o State novo como output
            //Precisamos informar no comando, quais são as chaves públicas das pessoas que precisam assinar esta transação,
            // para isso, pegamos todos os participantes das transações e acessamos o valor owningKey, que é a chave pública da pessoa
            val command = Command(IOUContract.Commands.Pay(), newSate.participants.map { it.owningKey })

            val txBuilder = TransactionBuilder(notary)
                .addInputState(oldState)
                .addOutputState(newSate, oldState.state.contract)
                .addCommand(command)

            //Assine a transação e colete a assinatura do Lender
            //Finalize a transação
            txBuilder.verify(serviceHub)
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)
            
            val flowSession = initiateFlow(newSate.lender)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(flowSession)))
            
            return subFlow(FinalityFlow(fullySignedTx))

        }
    }

    @InitiatedBy(Initiator::class)
    class Acceptor(val otherPartyFlow: FlowSession) : FlowLogic<SignedTransaction>(){
        @Suspendable
        override fun call(): SignedTransaction {
            TODO("Not yet implemented")
            val signTransactionFlow = object : SignTransactionFlow(otherPartyFlow) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.coreTransaction.outputsOfType<IOUState>().single()
                    "Eu devo ser o Lender deste IOU" using (output.lender == ourIdentity)

                    "Os pagamentos parciais precisam ser de no minimo a metade da divida" using (output.payment == output.value || output.payment >= (output.value/2))

                    val criteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(output.linearId))
                    val input = serviceHub.vaultService.queryBy<IOUState>(criteria).states.single().state.data

                    "O pagamento total deve ser realizado" using (output.payment == output.value || (output.payment != output.value && input.payment != 0))
                }
            }
            return subFlow(signTransactionFlow)
        }
    }



}