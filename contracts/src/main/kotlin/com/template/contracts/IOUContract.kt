package com.template.contracts

import com.template.states.IOUState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

open class IOUContract: Contract { //open: palabra reservada que indica que la clase puede ser extendida
    companion object { //instancia que ira acompanhar a classe, contem metodos e variaveis consideradas estaticas, unicas para aquela classe
        @JvmStatic
        val IOU_CONTRACT_ID = "com.example.contract.IOUContract"
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()
        when(command.value){
            is Commands.Create -> verifyCreate(tx)
            is Commands.Pay -> verifyPay(tx)
        }





    }

    //Todas as transações carregam com elas, comandos que informam qual tipo de operação será realizada.
    interface Commands : CommandData{
        class Create : Commands
        class Pay : Commands
    }

    //FUNCIONES
    private fun verifyCreate(tx: LedgerTransaction){
        val command = tx.commands.requireSingleCommand<Commands>()
        requireThat { //Ira a testar o valor que esta presente apos a palavra reservada using
            "No inputs should be consumed when issuing an IOU" using (tx.inputs.isEmpty())
            "Only one output state should be created" using (tx.outputs.size==1)

            val out = tx.outputsOfType<IOUState>().single()
            "The lender and the borrower cannot be the same entity" using (out.lender!=out.borrower)
            /*Como não temos consenso distribuído igual nas plataformas de tradicionais de blockchain,
            precisamos ter certeza que todas as partes envolvidas realmente concordaram em realizar a alteração
            e estão cientes que o dado já não está no mesmo estado, assim garantimos que todas as partes vão
            sempre estar enxergando a mesma verdade.
             */
            "All of the participants must be signers" using (command.signers.containsAll(out.participants.map { it.owningKey })) //Quem vai receber as transacoes
            "The IOU's value must be non-negative" using (out.value > 0 )

            "The IOU's payment value must be 0" using (out.payment == 0)
        }
    }

    private fun verifyPay(tx: LedgerTransaction){
        val command = tx.commands.requireSingleCommand<Commands>()
        requireThat {
            //Generic constraints around the IOU transaction
            "Only one input should be consumed when paying an IOU." using (tx.inputs.size==1)
            "Only one output state should be created." using (tx.outputs.size==1)
            val input = tx.inputsOfType<IOUState>().single()
            val output = tx.outputsOfType<IOUState>().single()
            "The input and output state should be the same." using (input.linearId == output.linearId)
            "All of the participants must be signers." using (command.signers.containsAll(output.participants.map { it.owningKey }))

            //IOU-specific constraints
            "O valor de pagamento no Output tem que ser maior que o valor de pagamento no Input" using (output.payment > input.payment)
            "O valor de pagamento no Output nao poder ser maior que o valor do emprestimo" using (output.payment <= input.value)
            "Apenas o valor de pagamento pode ser alterado." using (input.lender == output.lender &&
                                                                    input.borrower == output.borrower &&
                                                                    input.value == output.value)

        }
    }

}