//Simple Combinational Accelerator, based on the fortyTwo accelerator template.
// (c) Maddie Burbage, 2020
package combinations

import Chisel._
import freechips.rocketchip.tile._ //For LazyRoCC
import freechips.rocketchip.config._ //For Config
import freechips.rocketchip.diplomacy._ //For LazyModule
import freechips.rocketchip.rocket.{TLBConfig, HellaCacheReq} //something and level one cache

class Combinations(opcodes: OpcodeSet)(implicit p: Parameters) extends LazyRoCC(opcodes) {
    override lazy val module = new CombinationsImp(this)
}

class CombinationsImp(outer: Combinations)(implicit p: Parameters) extends LazyRoCCModuleImp(outer){

    val s_idle :: s_resp :: Nil = Enum(Bits(), 2)
    val state = Reg(init = s_idle) //state starts idle, is remembered


    val submodules = Array(Module(new FixedWeight()(p)), Module(new Lexicographic()(p)), Module(new GeneralCombinations()(p)))

    //Instruction inputs
    val length = Reg(io.cmd.bits.rs1(4,0)) //Length of binary string
    val previous = Reg(io.cmd.bits.rs2) //Previous binary string

    //Secondary Inputs
    val rd = Reg(io.cmd.bits.inst.rd) //Output location
    val function = Reg(io.cmd.bits.inst.funct) //Specific operation

    //Set up submodule inputs
    for(x <- submodules) {
	x.io.length := length
	x.io.previous := previous
    }

    //State-based communication values
    io.cmd.ready := (state === s_idle)
    io.busy := (state =/= s_idle)

    //When a new command is received, capture inputs and become busy
    when(io.cmd.fire()) {
        state := s_resp
        length := (io.cmd.bits.rs1(4,0))
        previous := io.cmd.bits.rs2
        rd := io.cmd.bits.inst.rd
        function := io.cmd.bits.inst.funct
    }

    val lookups = Array(a.length)
    while(i < submodules.length) {
        lookups(i) = (i.U, a(i))
    }
    //Obtain accelerator output from the correct submodule for the function
    io.resp.bits.data := MuxLookup(function, fixedWeightModule.io.out,
            lookups.map(_-> submodules[i]))
//Array(0.U->submodules[0].io.out, 1.U->lexicographicModule.io.out))

    io.resp.bits.rd := rd
    io.resp.valid := (state === s_resp)

    //After responding, become ready for new commands
    when(io.resp.fire()) {
        state := s_idle
    }

    //These features not used
    io.interrupt := Bool(false)
    io.mem.req.valid := Bool(false)

}

//Generates a fixed-weight binary string based on a previous string of the same
//weight and length. Binary strings up to length 32 will work.
class FixedWeight()(implicit p: Parameters) extends Submodule {

    //Calculations to generate the next combination
    val trimmed = io.previous & (io.previous + 1.U)
    val trailed = trimmed ^ (trimmed - 1.U)

    val indexShift = trailed + 1.U
    val indexTrailed = trailed & io.previous

    val subtracted = (indexShift & io.previous) - 1.U
    val fixed = Mux(subtracted.asSInt < 0.S, 0.U, subtracted)

    val result = io.previous + indexTrailed - fixed

    val stopper = 1.U(1.W) << io.length

    //Fill result with all 1s if finished
    io.out := Mux(result >> io.length =/= 0.U, ~(0.U), result % stopper)
}

//Generates the lexicographically-next binary string, up to a length of 32
class Lexicographic()(implicit p: Parameters) extends Submodule {
    val result = io.previous + 1.U
    io.out := Mux(((result >> io.length) & 1.U) === 1.U, ~(0.U), result)
}


//Generates the next binary string of a certain length based on the cool-er ordering
class GeneralCombinations()(implicit p: Parameters) extends Submodule {

    //Calculations
    val trailed = io.previous ^ (io.previous + 1.U)
    val mask = Mux(trailed > 3.U, trailed, ~(0.U))
    val lastPosition = (mask >> 1.U) + 1.U
    val first = Mux(trailed > 3.U, 1.U & previous, 1.U & ~previous)
    val shifted = (previous & mask) >> 1.U
    val rotated = Mux(first === 1.U, shifted | lastPosition, shifted)
    val result = rotated | (~mask & previous)

    io.out := Mux(result === ((1.U << length) - 1.U), ~(0.U), result)
}

//Base class for this accelerator's submodules
abstract class Submodule()(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val length = Input(UInt(5.W))
        val previous = Input(UInt(32.W))
        val out = Output(UInt(32.W))
    })
}

class WithCombinations extends Config((site, here, up) => {
    case BuildRoCC => Seq((p: Parameters) => {
        val Combinations = LazyModule.apply(new Combinations(OpcodeSet.custom0) (p))
        Combinations
    })
})

/**
 * Add this into the example project's RocketConfigs.scala file:

class CombinationsRocketConfig extends Config(
    new WithTop ++
    new WithBootROM ++
    new freechips.rocketchip.subsystem.WithInclusiveCache ++
    new combinations.WithCombinations ++
    new freechips.rocketchip.subsystem.WithNBigCores(1) ++
    new freechips.rocketchip.system.BaseConfig
)

 */