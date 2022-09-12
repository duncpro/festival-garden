package com.duncpro.festivalgarden.local

import com.duncpro.festivalgarden.sharedbackendutils.Lock
import com.duncpro.festivalgarden.sharedbackendutils.LockFactory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LocalLockFactory: LockFactory {
    private val lockMap = HashMap<String, Mutex>()
    private val acquisitionLock = Mutex()

    private inner class LocalLock constructor(val lockName: String): Lock {
        override suspend fun <T> withLock(action: suspend () -> T): T {
            val namedLock = acquisitionLock.withLock {
                val lock = lockMap.getOrPut(lockName, ::Mutex)
                lock.lock()
                lock
            }

            val result = try {
                action()
            } finally {
                namedLock.unlock()

                acquisitionLock.withLock {
                    val noOthersWaiting = namedLock.tryLock()
                    if (noOthersWaiting) {
                        lockMap.remove(lockName)
                        namedLock.unlock()
                    }
                }
            }

            return result
        }
    }

    override fun getLock(on: String): Lock = LocalLock(on)
}

