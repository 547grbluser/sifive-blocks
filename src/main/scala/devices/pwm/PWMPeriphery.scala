// See LICENSE for license details.
package sifive.blocks.devices.pwm

import Chisel._
import freechips.rocketchip.config.Field
import freechips.rocketchip.diplomacy.{LazyModule,LazyMultiIOModuleImp}
import freechips.rocketchip.chip.HasSystemNetworks
import freechips.rocketchip.tilelink.TLFragmenter
import freechips.rocketchip.util.HeterogeneousBag
import sifive.blocks.devices.pinctrl.{PinCtrl, Pin}

class PWMPortIO(val c: PWMParams) extends Bundle {
  val port = Vec(c.ncmp, Bool()).asOutput
  override def cloneType: this.type = new PWMPortIO(c).asInstanceOf[this.type]
}

class PWMPins[T <: Pin] (pingen: ()=> T, val c: PWMParams) extends Bundle {

  val pwm: Vec[T] = Vec(c.ncmp, pingen())

  override def cloneType: this.type =
    this.getClass.getConstructors.head.newInstance(pingen, c).asInstanceOf[this.type]

  def fromPWMPort(port: PWMPortIO) {
    (pwm zip port.port)  foreach {case (pin, port) =>
      pin.outputPin(port)
    }
  }
}

case object PeripheryPWMKey extends Field[Seq[PWMParams]]

trait HasPeripheryPWM extends HasSystemNetworks {
  val pwmParams = p(PeripheryPWMKey)
  val pwms = pwmParams map { params =>
    val pwm = LazyModule(new TLPWM(peripheryBusBytes, params))
    pwm.node := TLFragmenter(peripheryBusBytes, cacheBlockBytes)(peripheryBus.node)
    intBus.intnode := pwm.intnode
    pwm
  }
}

trait HasPeripheryPWMBundle {
  val pwm: HeterogeneousBag[PWMPortIO]

}

trait HasPeripheryPWMModuleImp extends LazyMultiIOModuleImp with HasPeripheryPWMBundle {
  val outer: HasPeripheryPWM
  val pwm = IO(HeterogeneousBag(outer.pwmParams.map(new PWMPortIO(_))))

  (pwm zip outer.pwms) foreach { case (io, device) =>
    io.port := device.module.io.gpio
  }
}
