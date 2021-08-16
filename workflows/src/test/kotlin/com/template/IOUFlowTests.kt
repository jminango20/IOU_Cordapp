package com.template

import com.template.flows.ExampleFlows
import com.template.states.IOUState
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class IOUFlowTests {
    lateinit var network: MockNetwork
    lateinit var a: StartedMockNode
    lateinit var b: StartedMockNode

    @Before
    fun setup(){
        network = MockNetwork(listOf("com.template.contract"))
        a = network.createPartyNode()
        b = network.createPartyNode()
        //For real nodes this happens automatically
        listOf(a,b).forEach {
            it.registerInitiatedFlow(ExampleFlows.Acceptor::class.java)
        }
        network.runNetwork()
    }

    @After
    fun tearDown(){
        network.stopNodes()
    }

    @Test
    fun `recorded transaction has no inputs and a single output, the input IOU`(){
        val iouValue = 1
        val flow = ExampleFlows.Initiator(iouValue, b.info.singleIdentity())
        val future = a.startFlow(flow)
        network.runNetwork()
        val signedTx = future.getOrThrow()

        //We check the recorded transaction in both vaults.
        for (node in listOf(a,b)){
            val recordedTx = node.services.validatedTransactions.getTransaction(signedTx.id)
            val txOutputs = recordedTx!!.tx.outputs
            assert(txOutputs.size == 1)

            val recordedState = txOutputs[0].data as IOUState
            assertEquals(recordedState.value, iouValue)
            assertEquals(recordedState.lender, a.info.singleIdentity())
            assertEquals(recordedState.borrower, b.info.singleIdentity())
        }


    }

}