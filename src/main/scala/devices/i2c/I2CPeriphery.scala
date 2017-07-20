// See LICENSE for license details.
package sifive.blocks.devices.i2c

import Chisel._
import freechips.rocketchip.config.Field
import freechips.rocketchip.diplomacy.{LazyModule,LazyMultiIOModuleImp}
import freechips.rocketchip.chip.{HasSystemNetworks}
import freechips.rocketchip.tilelink.TLFragmenter

case object PeripheryI2CKey extends Field[Seq[I2CParams]]

trait HasPeripheryI2C extends HasSystemNetworks {
  val i2cParams = p(PeripheryI2CKey)
  val i2c = i2cParams map { params =>
    val i2c = LazyModule(new TLI2C(peripheryBusBytes, params))
    i2c.node := TLFragmenter(peripheryBusBytes, cacheBlockBytes)(peripheryBus.node)
    intBus.intnode := i2c.intnode
    i2c
  }
}

trait HasPeripheryI2CBundle {
  val i2c: Vec[I2CPort]
}

trait HasPeripheryI2CModuleImp extends LazyMultiIOModuleImp with HasPeripheryI2CBundle {
  val outer: HasPeripheryI2C
  val i2c = IO(Vec(outer.i2cParams.size, new I2CPort))

  (i2c zip outer.i2c).foreach { case (io, device) =>
    io <> device.module.io.port
  }
}
