package io.github.ricardomonroy.tlacuache.storage

import kotlinx.coroutines.sync.Mutex

/**
 * `config.toml` lo escriben varias partes (favoritos, tema): cada una lee el texto, cambia su tabla con el
 * core y lo reescribe. Con un solo candado una no pisa el cambio de la otra.
 */
object ConfigLock {
    val mutex = Mutex()
}
