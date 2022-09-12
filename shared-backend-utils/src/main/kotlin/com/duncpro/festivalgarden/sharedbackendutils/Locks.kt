package com.duncpro.festivalgarden.sharedbackendutils

import com.amazonaws.services.dynamodbv2.AcquireLockOptions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBLockClient
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBLockClientOptions
import com.amazonaws.services.lambda.runtime.Context
import kotlinx.coroutines.yield
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.util.concurrent.TimeUnit

interface LockFactory {
    fun getLock(on: String): Lock
}

interface Lock {
    suspend fun <T> withLock(action: suspend () -> T): T
}

private class ServerlessLock(
    private val lambdaContext: Context,
    private val dynamoDB: DynamoDbClient,
    private val lockName: String,
    private val lockTableName: String
): Lock {

    override suspend fun <T> withLock(action: suspend () -> T): T {
        val options = AmazonDynamoDBLockClientOptions.builder(dynamoDB, lockTableName)
            .withOwnerName(lambdaContext.functionName)
            .withLeaseDuration(lambdaContext.remainingTimeInMillis.toLong())
            .withTimeUnit(TimeUnit.MILLISECONDS)
            .withCreateHeartbeatBackgroundThread(false)
            .build()

        AmazonDynamoDBLockClient(options).use { lockClient ->
            val lockOptions = AcquireLockOptions.builder(lockName)
                .withDeleteLockOnRelease(true)
                .withAcquireOnlyIfLockAlreadyExists(false)
                .withReentrant(false)
                .build()

            while (true) {
                val lock = lockClient.tryAcquireLock(lockOptions).orElse(null)
                if (lock == null) {
                    yield()
                    continue
                }

                return@withLock lock.use { action() }
            }
        }
    }

}

class ServerlessLockFactory(
    private val lambdaContext: Context,
    private val dynamoDB: DynamoDbClient,
    private val lockTableName: String
): LockFactory {
    override fun getLock(on: String): Lock = ServerlessLock(lambdaContext, dynamoDB, on, lockTableName)
}