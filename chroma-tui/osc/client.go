package osc

import (
	"github.com/hypebeast/go-osc/osc"
)

type Client struct {
	client *osc.Client
}

func NewClient(host string, port int) *Client {
	return &Client{
		client: osc.NewClient(host, port),
	}
}

func (c *Client) SendFloat(path string, value float32) error {
	msg := osc.NewMessage(path)
	msg.Append(value)
	return c.client.Send(msg)
}

func (c *Client) SendInt(path string, value int32) error {
	msg := osc.NewMessage(path)
	msg.Append(value)
	return c.client.Send(msg)
}

func (c *Client) SendSync() error {
	msg := osc.NewMessage("/chroma/sync")
	return c.client.Send(msg)
}

// Convenience methods for each parameter
func (c *Client) SetGain(v float32) error            { return c.SendFloat("/chroma/gain", v) }
func (c *Client) SetInputFreeze(v bool) error        { return c.SendInt("/chroma/inputFreeze", boolToInt(v)) }
func (c *Client) SetInputFreezeLength(v float32) error { return c.SendFloat("/chroma/inputFreezeLength", v) }
func (c *Client) SetFilterAmount(v float32) error    { return c.SendFloat("/chroma/filterAmount", v) }
func (c *Client) SetFilterCutoff(v float32) error    { return c.SendFloat("/chroma/filterCutoff", v) }
func (c *Client) SetFilterResonance(v float32) error { return c.SendFloat("/chroma/filterResonance", v) }
func (c *Client) SetGranularDensity(v float32) error { return c.SendFloat("/chroma/granularDensity", v) }
func (c *Client) SetGranularSize(v float32) error    { return c.SendFloat("/chroma/granularSize", v) }
func (c *Client) SetGranularPitchScatter(v float32) error { return c.SendFloat("/chroma/granularPitchScatter", v) }
func (c *Client) SetGranularPosScatter(v float32) error { return c.SendFloat("/chroma/granularPosScatter", v) }
func (c *Client) SetGranularMix(v float32) error     { return c.SendFloat("/chroma/granularMix", v) }
func (c *Client) SetGranularFreeze(v bool) error     { return c.SendInt("/chroma/granularFreeze", boolToInt(v)) }
func (c *Client) SetReverbDelayBlend(v float32) error { return c.SendFloat("/chroma/reverbDelayBlend", v) }
func (c *Client) SetDecayTime(v float32) error       { return c.SendFloat("/chroma/decayTime", v) }
func (c *Client) SetShimmerPitch(v float32) error    { return c.SendFloat("/chroma/shimmerPitch", v) }
func (c *Client) SetDelayTime(v float32) error       { return c.SendFloat("/chroma/delayTime", v) }
func (c *Client) SetModRate(v float32) error         { return c.SendFloat("/chroma/modRate", v) }
func (c *Client) SetModDepth(v float32) error        { return c.SendFloat("/chroma/modDepth", v) }
func (c *Client) SetReverbDelayMix(v float32) error  { return c.SendFloat("/chroma/reverbDelayMix", v) }
func (c *Client) SetOverdriveDrive(v float32) error { return c.SendFloat("/chroma/overdriveDrive", v) }
func (c *Client) SetOverdriveTone(v float32) error  { return c.SendFloat("/chroma/overdriveTone", v) }
func (c *Client) SetOverdriveMix(v float32) error   { return c.SendFloat("/chroma/overdriveMix", v) }
func (c *Client) SetBlendMode(v int) error           { return c.SendInt("/chroma/blendMode", int32(v)) }
func (c *Client) SetDryWet(v float32) error          { return c.SendFloat("/chroma/dryWet", v) }

func boolToInt(b bool) int32 {
	if b {
		return 1
	}
	return 0
}
