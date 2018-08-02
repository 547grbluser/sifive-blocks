// See LICENSE for license details.
package sifive.blocks.devices.spi

import Chisel._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.tilelink._

case class AttachedSPIParams(
  spi: SPIParams,
  controlXType: ClockCrossingType = NoCrossing,
  intXType: ClockCrossingType = NoCrossing)

case class AttachedSPIFlashParams(
  qspi: SPIFlashParams,
  fBufferDepth: Int = 0,
  controlXType: ClockCrossingType = NoCrossing,
  intXType: ClockCrossingType = NoCrossing,
  memXType: ClockCrossingType = NoCrossing)

object SPI {
  val nextId = { var i = -1; () => { i += 1; i} }
  def attach(params: AttachedSPIParams, controlBus: TLBusWrapper, intNode: IntInwardNode, mclock: Option[ModuleValue[Clock]])
            (implicit p: Parameters): TLSPI = {
    val name = s"spi_${nextId()}"
    val spi = LazyModule(new TLSPI(controlBus.beatBytes, params.spi))
    spi.suggestName(name)

    controlBus.coupleTo(s"device_named_$name") {
      spi.controlXing(params.controlXType) := TLFragmenter(controlBus.beatBytes, controlBus.blockBytes) := _
    }

    intNode := spi.intXing(params.intXType)

    InModuleBody { spi.module.clock := mclock.map(_.getWrappedValue).getOrElse(controlBus.module.clock) }

    spi
  }

  val nextFlashId = { var i = -1; () => { i += 1; i} }
  def attachFlash(params: AttachedSPIFlashParams, controlBus: TLBusWrapper, memBus: TLBusWrapper, intNode: IntInwardNode, mclock: Option[ModuleValue[Clock]])
                 (implicit p: Parameters): TLSPIFlash = {
    val name = s"qspi_${nextFlashId()}" // TODO should these be shared with regular SPIs?
    val qspi = LazyModule(new TLSPIFlash(controlBus.beatBytes, params.qspi))
    qspi.suggestName(name)

    controlBus.coupleTo(s"device_named_$name") {
      qspi.controlXing(params.controlXType) := TLFragmenter(controlBus.beatBytes, controlBus.blockBytes) := _
    }

    memBus.coupleTo(s"mem_named_$name") {
      (qspi.memXing(params.memXType)
        := TLFragmenter(1, memBus.blockBytes)
        := TLBuffer(BufferParams(params.fBufferDepth), BufferParams.none)
        := TLWidthWidget(memBus.beatBytes)
        := _)
    }

    intNode := qspi.intXing(params.intXType)

    InModuleBody { qspi.module.clock := mclock.map(_.getWrappedValue).getOrElse(controlBus.module.clock) }

    qspi
  }

  def synchronize(q: SPIPortIO): SPIPortIO = {
    val x = Wire(new SPIPortIO(q.c))//, DontCare)
    x.sck := q.sck
    x.cs := q.cs
    q.dq.zip(x.dq).foreach { case(qq,xx) =>
      qq.i := ShiftRegister(xx.i, q.c.sampleDelay)
      xx.o := qq.o
      xx.oe := qq.oe
    }
    x
  }
}
