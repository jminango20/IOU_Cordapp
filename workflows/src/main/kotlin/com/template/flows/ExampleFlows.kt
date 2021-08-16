package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.template.contracts.IOUContract
import net.corda.core.flows.*
import net.corda.core.utilities.ProgressTracker
import net.corda.core.flows.FinalityFlow

import net.corda.core.flows.CollectSignaturesFlow

import net.corda.core.transactions.SignedTransaction

import java.util.stream.Collectors

import net.corda.core.flows.FlowSession

import net.corda.core.identity.Party

import com.template.contracts.TemplateContract
import com.template.states.IOUState

import net.corda.core.transactions.TransactionBuilder

import com.template.states.TemplateState
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.identity.AbstractParty
import java.time.Instant


// *********
// * Flows *
// *********
object ExampleFlows {

    @InitiatingFlow
    @StartableByRPC
    class Initiator(val iouValue:Int,
                    val otherParty: Party) : FlowLogic<SignedTransaction>(){

        //Um FlowLogic é um fluxo de negócio e no final irá retornar uma váriavel do tipo T quando a função call for executada.

        @Suspendable
        override fun call(): SignedTransaction {
            //ourIdentity: informações sobre o nó atual.
            //serviceHub: hub de serviços do Corda, utilizado para acessar os serviços implementados no nó.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]
            val iouState = IOUState(iouValue,serviceHub.myInfo.legalIdentities.first(),otherParty, Instant.now(), interest = 0)
            val txCommand = Command(IOUContract.Commands.Create(), iouState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary).addOutputState(iouState, IOUContract.IOU_CONTRACT_ID)
                .addCommand(txCommand)
            txBuilder.verify(serviceHub)
            //signInitialTransaction: realiza a primeira assinatura de uma transação
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)
            //initiateFlow: Abre uma sessão com outro nó.
            val otherPartyFlow = initiateFlow(otherParty)
            //CollectSignaturesFlow: Flow que recebe uma transação assinada e envia para outros nós um pedido de assinatura.
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(otherPartyFlow)))
            //FinalityFlow: Notoriza a transação e grava os dados em todos os nós participantes da transação em caso de sucesso.
            return subFlow(FinalityFlow(fullySignedTx))
        }
    }


    @InitiatedBy(Initiator::class)
    class Acceptor(val otherPartyFlow: FlowSession) : FlowLogic<SignedTransaction>(){
        @Suspendable
        override fun call(): SignedTransaction {
            val signedTransactionFlow = object  : SignTransactionFlow(otherPartyFlow){
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    //Validacoes
                    val output = stx.tx.outputs.single().data
                    "This must be an IOU transaction" using (output is IOUState)

                    val iou = output as IOUState
                    "I won't accept IOUs with a value over 100" using (iou.value <= 100)
                }
            }
            return subFlow(signedTransactionFlow)
        }
    }

}